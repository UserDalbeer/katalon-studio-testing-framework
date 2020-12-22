package com.kms.katalon.core.webservice.helper;

import java.net.HttpURLConnection;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;

import com.kms.katalon.core.configuration.RunConfiguration;
import com.kms.katalon.core.logging.KeywordLogger;
import com.kms.katalon.core.testobject.RequestObject;
import com.kms.katalon.core.testobject.ResponseObject;
import com.kms.katalon.core.webservice.common.ServiceRequestFactory;
import com.kms.katalon.core.webservice.constants.StringConstants;
import com.kms.katalon.core.webservice.util.WebServiceCommonUtil;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

public class WebServiceCommonHelper {
	
    private static final KeywordLogger logger = KeywordLogger.getInstance(WebServiceCommonHelper.class);

    public static ResponseObject sendRequest(RequestObject request) throws Exception {
        configRequestTimeout(request);
        configRequestResponseSizeLimit(request);
        ResponseObject responseObject = ServiceRequestFactory.getInstance(request).send(request);
        return responseObject;
    }
    
    public static void configRequestTimeout(RequestObject request) {
        if (RunConfiguration.canCustomizeRequestTimeout()) {
            Map<String, Object> executionSettings = RunConfiguration.getExecutionGeneralProperties();

            if (WebServiceCommonUtil.isUnsetRequestTimeout(request.getConnectionTimeout())) {
                Object connectionTimeout = executionSettings.get(RunConfiguration.REQUEST_CONNECTION_TIMEOUT);
                if (connectionTimeout != null) {
                    int connectionTimeoutIntVal = ((Number) connectionTimeout).intValue();
                    request.setConnectionTimeout(connectionTimeoutIntVal);
                }
            }

            if (WebServiceCommonUtil.isUnsetRequestTimeout(request.getSocketTimeout())) {
                Object socketTimeout = executionSettings.get(RunConfiguration.REQUEST_SOCKET_TIMEOUT);
                if (socketTimeout != null) {
                    int socketTimeoutIntVal = ((Number) socketTimeout).intValue();
                    request.setSocketTimeout(socketTimeoutIntVal);
                }
            }
        } else {
            request.setConnectionTimeout(RequestObject.TIMEOUT_UNSET);
            request.setSocketTimeout(RequestObject.TIMEOUT_UNSET);
        }
    }

    public static void configRequestResponseSizeLimit(RequestObject request) {
        if (RunConfiguration.canCustomizeRequestResponseSizeLimit()) {
            Map<String, Object> executionSettings = RunConfiguration.getExecutionGeneralProperties();

            if (WebServiceCommonUtil.isUnsetMaxRequestResponseSize(request.getMaxResponseSize())) {
                Object maxResponseSize = executionSettings.get(RunConfiguration.REQUEST_MAX_RESPONSE_SIZE);
                if (maxResponseSize != null) {
                    long maxResponseSizeLongVal = ((Number) maxResponseSize).longValue();
                    request.setMaxResponseSize(maxResponseSizeLongVal);
                }
            }
        } else {
            request.setMaxResponseSize(RequestObject.MAX_RESPONSE_SIZE_UNSET);
        }
    }
    
	public static void checkRequestObject(RequestObject requestObject) throws IllegalArgumentException {
	    logger.logDebug(StringConstants.KW_LOG_INFO_CHECKING_REQUEST_OBJECT);
		if (requestObject == null) {
			throw new IllegalArgumentException(StringConstants.KW_LOG_FAILED_REQUEST_OBJECT_IS_NULL);
		}
	}
	
	public static void checkResponseObject(ResponseObject responseObject) throws IllegalArgumentException {
	    logger.logDebug(StringConstants.KW_LOG_INFO_CHECKING_RESPONSE_OBJECT);
		if (responseObject == null) {
			throw new IllegalArgumentException(StringConstants.KW_LOG_FAILED_RESPONSE_OBJECT_IS_NULL);
		}
	}
	
	public static void checkResponseObjectContent(ResponseObject responseObject) throws Exception {
	    logger.logDebug(StringConstants.KW_LOG_INFO_CHECKING_RESPONSE_OBJECT_CONTENT);
		if (responseObject.getResponseBodyContent() == null) {
			throw new IllegalArgumentException(StringConstants.KW_LOG_FAILED_RESPONSE_OBJECT_CONTENT_IS_NULL);
		}
	}
	
	public static Object parseAndExecuteExpressionForXml(String locator, String groovyFunction, String xmlText){
		String[] tokens = locator.split("\\.");
		String rootName = "";
		String locatorExp = "";
		for(int i=0; i<tokens.length; i++){
			String token = tokens[i];
			locatorExp += token;
			if(i < tokens.length -1){
				locatorExp += ".";
			}
			else if(i == tokens.length -1){
				locatorExp += "." + groovyFunction;
			}
			if(i==0){
				rootName = token;
			}
		}
		StringBuilder groovyScript = new StringBuilder();
		groovyScript.append("def "+ rootName +" = new XmlSlurper().parseText(xmlText);");
		groovyScript.append("return " + locatorExp);

		Binding binding = new Binding();
		binding.setVariable("xmlText", xmlText);
		GroovyShell shell = new GroovyShell(binding);
		return shell.evaluate(groovyScript.toString());
	}

	public static Object parseAndGetPropertyValueForXml(String locator, String xmlText){
        String[] tokens = locator.split("\\.");
        String rootName = "";
        String locatorExp = "";
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            locatorExp += token;
            if (i < tokens.length - 1) {
                locatorExp += ".";
            }

            if (i == 0) {
                rootName = token;
            }
        }
        StringBuilder groovyScript = new StringBuilder();
        groovyScript.append("def " + rootName + " = new XmlSlurper().parseText(xmlText);");
        groovyScript.append("return " + locatorExp);

        Binding binding = new Binding();
        binding.setVariable("xmlText", xmlText);
        GroovyShell shell = new GroovyShell(binding);
        return shell.evaluate(groovyScript.toString());
	}

	public static Object parseAndExecuteExpressionForJson(String locator, String groovyFunction, String jsonText){
		boolean needAppendRoot = true;
		String[] tokens = locator.split("\\.");
		String locatorExp = "";
		for(int i=0; i<tokens.length; i++){
			String token = tokens[i];
			locatorExp += token;
			if(i == 0){
				if(token.matches("\\[\\d+\\]")){
					needAppendRoot = false;	
				}
			}
			if(i < tokens.length -1){
				locatorExp += ".";
			}
			else if(i == tokens.length -1){
				if(!locatorExp.equals("")){
					locatorExp += ".";
				}
				locatorExp += groovyFunction;
			}
		}
		StringBuilder groovyScript = new StringBuilder();
		groovyScript.append("import groovy.json.JsonSlurper;");
		groovyScript.append("def root = new JsonSlurper().parseText(jsonText);");
		if(needAppendRoot){
			groovyScript.append("return root." + locatorExp +";");	
		}
		else{
			groovyScript.append("return root" + locatorExp +";");
		}

		Binding binding = new Binding();
		binding.setVariable("jsonText", jsonText);
		GroovyShell shell = new GroovyShell(binding);
		return shell.evaluate(groovyScript.toString());
	}

	public static Object parseAndGetPropertyValueForJson(String locator, String jsonText){
		boolean needAppendRoot = true;
		String[] tokens = locator.split("\\.");
		String locatorExp = "";
		for(int i=0; i<tokens.length; i++){
			String token = tokens[i];
			locatorExp += token;
			if(i == 0){
				if(token.matches("\\[\\d+\\]")){
					needAppendRoot = false;	
				}
			}
			if(i < tokens.length -1){
				locatorExp += ".";
			}
		}
		StringBuilder groovyScript = new StringBuilder();
		groovyScript.append("import groovy.json.JsonSlurper;");
		groovyScript.append("def root = new JsonSlurper().parseText(jsonText);");
		if(needAppendRoot){
			groovyScript.append("return root." + locatorExp +";");	
		}
		else{
			groovyScript.append("return root" + locatorExp +";");
		}
		
		Binding binding = new Binding();
		binding.setVariable("jsonText", jsonText);
		GroovyShell shell = new GroovyShell(binding);
		return shell.evaluate(groovyScript.toString());
	}

    public static long calculateHeaderLength(HttpURLConnection conn) {
        long headerLength = conn.getHeaderFields().entrySet().stream().mapToLong(e -> {
            String key = e.getKey();
            if (StringUtils.isEmpty(key)) {
                return 0L;
            }
            long length = key.getBytes().length;
            length += e.getValue().stream().mapToLong(v -> v.getBytes().length).sum();
            return length;
        }).sum();

        return headerLength;
    }
    
    public static long calculateHeaderLength(HttpResponse httpResponse) {
        Header[] headers = httpResponse.getAllHeaders();
        long headerLength = 0;
        for (Header header : headers) {
            String key = header.getName();
            if (StringUtils.isEmpty(key)) {
                return 0L;
            }
            headerLength += key.getBytes().length;
            headerLength += header.getValue().getBytes().length;
        }
        return headerLength;
    }
}
