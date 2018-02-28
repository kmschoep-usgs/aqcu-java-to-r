package gov.usgs.aqcu.r;

/**
 * This enum is temporary while we support two styles of rendering in the rserve (rmarkdown and Whisker).
 * Eventually we will unify to use just mustache and this and all logic which uses this will go away.
 * 
 * TODO remove when repgen does not support two styles
 * 
 * Remove converted reports from this list.
 *  
 * @author thongsav
 *
 */
public enum OldRenderingMethodReports {
	extremes,
	vdiagram,
	uvhydrograph,
	sitevisitpeak,
	sensorreadingsummary,
	correctionsataglance,
	dvhydrograph,
	fiveyeargwsum;
}
