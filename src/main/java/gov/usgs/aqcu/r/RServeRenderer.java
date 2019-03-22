package gov.usgs.aqcu.r;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RFileInputStream;
import org.rosuda.REngine.Rserve.RFileOutputStream;
import org.rosuda.REngine.Rserve.RserveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RServeRenderer {
	private static final Logger log = LoggerFactory.getLogger(RServeRenderer.class);

	//TODO remove R_OLD_RENDER_URL_COMMAND_FORMAT when all reports use new whisker rendering
	private static final String R_OLD_RENDER_URL_COMMAND_FORMAT = "{0}(fromJSON(\"{1}\"), \"{2}\")";
	private static final String R_RENDER_COMMAND_FORMAT = "renderReport(fromJSON(\"{0}\"), \"{1}\", \"{2}\" )";

	private static final String R_DEPENDENCIES_LOAD = "library('jsonlite');library('knitr');library('gsplot');library('repgen');library('dplyr');library('lubridate');library('whisker');library('readr');Sys.setenv(TZ='UTC');";
	private static final String R_VERSION_INFO_CMD = "repgen:::printVersionStrings()";

	private static final int REPGEN_RENDER_RETRIES = 3;

	private RConnectionFactory rConnectionFactory;

	@Autowired
	public RServeRenderer(RConnectionFactory rConnectionFactory) {
		this.rConnectionFactory = rConnectionFactory;
	}

	public byte[] render(String requestingUser, String reportType, String reportJson) {
		byte[] renderedReport = new byte[0];
		String home = null;
		String workspace = null;
		RConnection c = null;
		try {
			c = rConnectionFactory.getConnection();
			home = getwd(c);
			workspace = createAndEnterWorkspace(c);
			String fileName = runRepGen(c, requestingUser, reportType, reportJson);
			renderedReport = retrieveRenderedBytes(c, fileName);
		} catch (RserveException | REXPMismatchException | IOException | InterruptedException | PandocErrorOneException e) {
			log.warn("Error interacting with Rserve", e);
			throw new RuntimeException("There was an error while rendering with Rserve, " + 
					e.getClass().getName() + ": "+ e.getMessage());
		} finally {
			if (c != null) {
				log.trace("Closing connection to Rserve");
				try {
					setwd(c, home);
				} catch (Exception e) {
					log.warn("Error resetting the wd for the R connection", e);
				}
				try {
					removeWorkspace(c, workspace);
				} catch (Exception e) {
					log.warn("Error removing the workspace for the R connection", e);
				}
				try {
					c.close();
				} catch (Exception e) {
					log.warn("Error closing the R connection", e);
				}
			}
		}

		return renderedReport;
	}

	private String createAndEnterWorkspace(RConnection c) throws RserveException, REXPMismatchException, PandocErrorOneException {
		StringBuilder wd = new StringBuilder();
		wd.append(getNewRequestId("workspace"));

		String workspace = wd.toString();
		createDir(c, workspace);
		setwd(c, workspace);
		return workspace;
	}

	private String getwd(RConnection c) throws RserveException, REXPMismatchException, PandocErrorOneException {
		return parseAndEval(c, "getwd()").asString();
	}

	private void setwd(RConnection c, String workspace) throws RserveException, REXPMismatchException, PandocErrorOneException {
		parseAndEval(c, "setwd('" + workspace + "')");
	}

	private void createDir(RConnection c, String dir) throws RserveException, PandocErrorOneException {
		parseAndEval(c, "dir.create(\"" + dir + "\")");
	}

	private void removeWorkspace(RConnection c, String workspace) throws RserveException, PandocErrorOneException {
		parseAndEval(c, "(unlink(\"" + workspace + "\", recursive=TRUE))");
	}

	private String runRepGen(RConnection c, String requestingUser, String reportType, String reportJson) throws RserveException, REXPMismatchException, IOException {
		try {
			parseAndEval(c, R_DEPENDENCIES_LOAD);
		} catch (PandocErrorOneException e) {
			throw new RuntimeException("Unable to run " + R_DEPENDENCIES_LOAD);
		}

		//transfer JSON over into file
		String filename = writeJsonToFile(c, reportJson);
		REXP renderReportExp = null;
		int renderAttempts = 0;

		while(renderReportExp == null) {
			try {
				renderAttempts++;
				String renderCommand;

				try {
					//TODO when OldRenderingMethodReports goes away, remove try catch and always use the new render command
					//this will detect if report type is supported under old method
					OldRenderingMethodReports.valueOf(reportType);
					renderCommand = MessageFormat.format(R_OLD_RENDER_URL_COMMAND_FORMAT, reportType, filename, requestingUser);
				} catch(Exception e) {
					renderCommand = MessageFormat.format(R_RENDER_COMMAND_FORMAT, filename, reportType, requestingUser);
				}
				log.info("Starting rendering for " + reportType + ".");
				long startTime = System.nanoTime();
				renderReportExp = parseAndEval(c, renderCommand);
				long durationMs = (System.nanoTime() - startTime)/1000000;
				log.info("Finished rendering for " + reportType + " in " + durationMs + " ms.");
			} catch (PandocErrorOneException e) {
				if(renderAttempts < REPGEN_RENDER_RETRIES) {
					log.warn("Failed to render " + reportType + "report on "
							+ renderAttempts + " of " + REPGEN_RENDER_RETRIES + " attempts. Retrying...");
				} else {
					log.error("Failed to render report. Rendering service may be overloaded.", e);
					throw new RuntimeException("Failed to render report. Rendering service may be overloaded.", e);
				}
			}
		}

		return renderReportExp.asString();
	}

	/**
	 * 
	 * TODO this exception might no longer be needed when we complete the transition to Whisker, may remove along with OldRenderingMethodReports
	 * when transition is complete
	 * 
	 * HACK ALERT
	 * 
	 * This exception is intentionally vague as the source pandoc error 1 exception is equally as vague. We are explicitly 
	 * catching this exception so that we can retry. This error seems to be intermittent and related to high loads. 
	 * We are avoiding the error for now with retries.
	 * 
	 * The real fix is figuring out why repgen and pandoc does this. 
	 * 
	 * @author thongsav	 *
	 */
	private static class PandocErrorOneException extends Exception {
		private static final long serialVersionUID = 1L;

		public PandocErrorOneException(String rError) {
			super(rError);
		}

		public static boolean matches(String error) {
			return error.trim().toLowerCase().endsWith("pandoc document conversion failed with error 1");
		}
	}

	private static REXP parseAndEval(RConnection c, String rOperation) throws PandocErrorOneException {
		String op = "try({"+ rOperation +"},silent=TRUE)";
		try {
			REXP r = c.parseAndEval(op);
			if (r.inherits("try-error")) { 
				String rError = r.asString();

				if(PandocErrorOneException.matches(rError)) {
					throw new PandocErrorOneException(rError);
				} else {
					log.error("Error executing " + op +": " + rError);
					throw new RuntimeException(r.asString() + " attempting to run: " +  op);
				}
			}
			return r;

		} catch (REngineException | REXPMismatchException e) {
			String msg = "Error: eval'ing repgen: "+ op + e.getMessage();
			log.error(msg, e);
			throw new RuntimeException(msg, e);
		}
	}

	private static byte[] retrieveRenderedBytes(RConnection c, String filename) throws RserveException, REXPMismatchException, IOException {
		RFileInputStream rstream = c.openFile(filename);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		IOUtils.copy(rstream, out);
		return out.toByteArray();
	}

	private static String writeJsonToFile(RConnection c, String reportJson) throws RserveException, REXPMismatchException, IOException {
		String filename = getNewRequestId("report") + ".json";
		RFileOutputStream rstream = null;
		try {
			rstream = c.createFile(filename);
			rstream.write(reportJson.getBytes());
		} catch (Exception e) {
			throw new RuntimeException("Error writing json to rserve for rendering", e);
		} finally {
			try {
				rstream.close();
			} catch (Exception e) {}
		}
		return filename;
	}

	public String getVersionInfo() {
		String result = "NA";
		RConnection c = null;
		try {
			c = rConnectionFactory.getConnection();
			result = parseAndEval(c, R_DEPENDENCIES_LOAD + R_VERSION_INFO_CMD).asString();
		} catch (RserveException | REXPMismatchException | InterruptedException | PandocErrorOneException e) {
			log.warn("Error interacting with Rserve", e);
			throw new RuntimeException("There was an error while rendering with Rserve, " + 
					e.getClass().getName() + ": "+ e.getMessage());
		} finally {
			if(c != null) {
				log.trace("Closing connection to Rserve");

				try {
					c.close();
				} catch (Exception e) {
					log.warn("Error closing the R connection", e);
				}
			}
		}

		return result;
	}

	public static String getNewRequestId(String label) {
		return label + "-" + UUID.randomUUID().toString();
	}

}