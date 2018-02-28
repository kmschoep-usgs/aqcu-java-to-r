package gov.usgs.aqcu;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gov.usgs.aqcu.r.RServeRenderer;
import io.swagger.annotations.ApiParam;

@RestController
public class JavaToRServeController {

	private RServeRenderer rServeRenderer;

	@Autowired
	public JavaToRServeController(RServeRenderer rServeRenderer) {
		this.rServeRenderer = rServeRenderer;
	}

	@PostMapping("report/{reportType}")
	public byte[] getReport(
			@ApiParam(required=true,
				allowableValues="example,xformExample,flattenXformExample,extremes,vdiagram,rating,uvhydrograph,sitevisitpeak,sensorreadingsummary,correctionsataglance,timeseriessummary,dvhydrograph,fiveyeargwsum,derivationchain")
			@PathVariable("reportType") String reportType,
			@RequestParam(value="requestingUser", required=false) String requestingUser,
			@RequestBody String reportJson) throws IOException {
		return rServeRenderer.render(requestingUser, reportType, reportJson);
	}

	@GetMapping(value="rserveversion", produces=MediaType.TEXT_PLAIN_VALUE)
	public String getRServeVersion() {
		return rServeRenderer.getVersionInfo();
	}

	@GetMapping(value="version", produces=MediaType.TEXT_PLAIN_VALUE)
	public String getVersion() {
		return ApplicationVersion.getVersion();
	}

}

