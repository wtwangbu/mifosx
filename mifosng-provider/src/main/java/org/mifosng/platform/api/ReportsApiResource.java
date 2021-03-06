package org.mifosng.platform.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.mifosng.platform.api.data.GenericResultsetData;
import org.mifosng.platform.api.infrastructure.ApiJsonSerializerService;
import org.mifosng.platform.api.infrastructure.ApiParameterHelper;
import org.mifosng.platform.exceptions.NoAuthorizationException;
import org.mifosng.platform.noncore.ReadReportingService;
import org.mifosng.platform.security.PlatformSecurityContext;
import org.mifosng.platform.user.domain.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/reports")
@Component
@Scope("singleton")
public class ReportsApiResource {

	private final PlatformSecurityContext context;

	@Autowired
	public ReportsApiResource(final PlatformSecurityContext context) {
		this.context = context;
	}

	@Autowired
	private ReadReportingService readExtraDataAndReportingService;

	@Autowired
	private ApiJsonSerializerService apiJsonSerializerService;

	@GET
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON, "application/x-msdownload" })
	public Response retrieveReportList(@Context final UriInfo uriInfo) {

		Map<String, String> extractedQueryParams = new HashMap<String, String>();

		boolean prettyPrint = ApiParameterHelper.prettyPrint(uriInfo
				.getQueryParameters());
		boolean exportCsv = ApiParameterHelper.exportCsv(uriInfo
				.getQueryParameters());

		if (!exportCsv) {
			GenericResultsetData result = this.readExtraDataAndReportingService
					.retrieveGenericResultset(".", ".", extractedQueryParams);

			final String json = this.apiJsonSerializerService
					.serializeGenericResultsetDataToJson(prettyPrint, result);

			return Response.ok().entity(json).build();
		}

		StreamingOutput result = this.readExtraDataAndReportingService
				.retrieveReportCSV(".", ".", extractedQueryParams);

		return Response
				.ok()
				.entity(result)
				.header("Content-Disposition",
						"attachment;filename=ReportList.csv").build();
	}

	@GET
	@Path("{reportName}")
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON, "application/x-msdownload",
			"application/vnd.ms-excel", "application/pdf", "text/html" })
	public Response retrieveReport(
			@PathParam("reportName") final String reportName,
			@Context final UriInfo uriInfo) {

		MultivaluedMap<String, String> queryParams = uriInfo
				.getQueryParameters();

		boolean prettyPrint = ApiParameterHelper.prettyPrint(uriInfo
				.getQueryParameters());
		boolean exportCsv = ApiParameterHelper.exportCsv(uriInfo
				.getQueryParameters());
		boolean parameterType = ApiParameterHelper.parameterType(uriInfo
				.getQueryParameters());

		checkUserPermissionForReport(reportName, parameterType);

		String parameterTypeValue = null;
		if (!parameterType) {
			parameterTypeValue = "report";
			if (this.readExtraDataAndReportingService.getReportType(reportName)
					.equalsIgnoreCase("Pentaho")) {
				Map<String, String> reportParams = getReportParams(queryParams,
						true);
				return this.readExtraDataAndReportingService
						.processPentahoRequest(reportName,
								queryParams.getFirst("output-type"),
								reportParams);
			}
		} else {
			parameterTypeValue = "parameter";
		}

		if (!exportCsv) {

			Map<String, String> reportParams = getReportParams(queryParams,
					false);

			GenericResultsetData result = this.readExtraDataAndReportingService
					.retrieveGenericResultset(reportName, parameterTypeValue,
							reportParams);

			final String json = this.apiJsonSerializerService
					.serializeGenericResultsetDataToJson(prettyPrint, result);

			return Response.ok().entity(json).type(MediaType.APPLICATION_JSON)
					.build();
		}

		// CSV Export
		Map<String, String> reportParams = getReportParams(queryParams, false);
		StreamingOutput result = this.readExtraDataAndReportingService
				.retrieveReportCSV(reportName, parameterTypeValue, reportParams);

		return Response
				.ok()
				.entity(result)
				.type("application/x-msdownload")
				.header("Content-Disposition",
						"attachment;filename=" + reportName.replaceAll(" ", "")
								+ ".csv").build();
	}

	private void checkUserPermissionForReport(String reportName,
			boolean parameterType) {

		// Anyone can run a 'report' that is simply getting possible parameter
		// (dropdown listbox) values.

		if (!parameterType) {
			AppUser currentUser = context.authenticatedUser();
			if (currentUser.hasNotPermissionForReport(reportName)) {
				// TODO - message isnt passing back the message in the string in
				// the json just a generalised not authorised message
				throw new NoAuthorizationException(
						"Not Authorised to Run Report: " + reportName);
			}
		}
	}

	private Map<String, String> getReportParams(
			final MultivaluedMap<String, String> queryParams,
			final Boolean isPentaho) {

		Map<String, String> reportParams = new HashMap<String, String>();
		Set<String> keys = queryParams.keySet();
		String pKey;
		String pValue;
		for (String k : keys) {

			if (k.startsWith("R_")) {
				if (isPentaho)
					pKey = k.substring(2);
				else
					pKey = "${" + k.substring(2) + "}";

				pValue = queryParams.get(k).get(0);
				reportParams.put(pKey, pValue);
			}
		}
		return reportParams;
	}

}