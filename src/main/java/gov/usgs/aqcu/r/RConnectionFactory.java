package gov.usgs.aqcu.r;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RConnectionFactory {
	private Logger log = LoggerFactory.getLogger(RConnectionFactory.class);

	@Value("${rserve.host}")
	private String host;
	@Value("${rserve.port}")
	private int port;
	@Value("${rserve.user}")
	private String user;
	@Value("${rserve.timeout.ms}")
	private long timeout;
	@Value("${rserve.retry.interval.ms}")
	private long retryInterval;
	@Value("${rserve.password}")
	private String pass;

	public RConnection getConnection() throws RserveException, InterruptedException {
		RConnection c = null;

		long start = System.currentTimeMillis();
		do {
			try {
				if (host != null) {
					c = new RConnection(host, port);
				} else {
					c = new RConnection();
				}
			} catch (RserveException e) {
				log.warn("Trying to connect to rserve, retry " + retryInterval);
				Thread.sleep(retryInterval);
			}
			if (System.currentTimeMillis() - start > timeout) {
				try {
					c.close();
				} catch(Exception e) {
					log.error("Exception while closing connection: ", e.getMessage());
				}
				throw new RserveException(c, "Timeout waiting for connection");
				
			}
		} while (c == null);

		if (user != null) {
			c.login(user, pass);
		}

		return c;
	}

}

