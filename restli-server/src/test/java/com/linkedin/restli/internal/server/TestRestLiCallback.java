/*
   Copyright (c) 2014 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.linkedin.restli.internal.server;


import com.linkedin.data.DataMap;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.r2.message.rest.RestException;
import com.linkedin.r2.message.rest.RestRequest;
import com.linkedin.r2.message.rest.RestResponse;
import com.linkedin.r2.message.rest.RestResponseBuilder;
import com.linkedin.restli.common.ErrorResponse;
import com.linkedin.restli.common.HttpStatus;
import com.linkedin.restli.common.ResourceMethod;
import com.linkedin.restli.common.RestConstants;
import com.linkedin.restli.internal.common.AllProtocolVersions;
import com.linkedin.restli.internal.common.HeaderUtil;
import com.linkedin.restli.internal.server.filter.FilterResponseContextInternal;
import com.linkedin.restli.internal.server.methods.response.PartialRestResponse;
import com.linkedin.restli.server.RequestExecutionCallback;
import com.linkedin.restli.server.RequestExecutionReport;
import com.linkedin.restli.server.RequestExecutionReportBuilder;
import com.linkedin.restli.server.RestLiResponseDataException;
import com.linkedin.restli.server.RestLiServiceException;
import com.linkedin.restli.server.RoutingException;
import com.linkedin.restli.server.filter.FilterRequestContext;
import com.linkedin.restli.server.filter.FilterResponseContext;
import com.linkedin.restli.server.filter.ResponseFilter;

import java.util.Arrays;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.Maps;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;


/**
 * @author nshankar
 */
public class TestRestLiCallback
{
  @Mock
  private RestRequest _restRequest;
  @Mock
  private RoutingResult _routingResult;
  @Mock
  private RestLiResponseHandler _responseHandler;
  @Mock
  private RequestExecutionCallback<RestResponse> _callback;

  private RestLiCallback<Object> _noFilterRestLiCallback;

  private RestLiCallback<Object> _twoFilterRestLiCallback;

  @Mock
  private FilterRequestContext _filterRequestContext;

  @Mock
  private ResponseFilter _filter;

  @BeforeTest
  protected void setUp() throws Exception
  {
    MockitoAnnotations.initMocks(this);
    _noFilterRestLiCallback =
        new RestLiCallback<Object>(_restRequest, _routingResult, _responseHandler, _callback, null, null);
    _twoFilterRestLiCallback =
        new RestLiCallback<Object>(_restRequest, _routingResult, _responseHandler, _callback, Arrays.asList(_filter,
                                                                                                            _filter),
                                   _filterRequestContext);
  }

  @AfterMethod
  protected void resetMocks()
  {
    reset(_filter, _filterRequestContext, _restRequest, _routingResult, _responseHandler, _callback);
  }

  @Test
  public void testOnSuccessNoFilters() throws Exception
  {
    String result = "foo";
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    AugmentedRestLiResponseData responseData = new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).build();
    PartialRestResponse partialResponse = new PartialRestResponse.Builder().build();
    RestResponse restResponse = new RestResponseBuilder().build();
    // Set up.
    when(_responseHandler.buildRestLiResponseData(_restRequest, _routingResult, result)).thenReturn(responseData);
    when(_responseHandler.buildPartialResponse(_routingResult, responseData)).thenReturn(partialResponse);
    when(_responseHandler.buildResponse(_routingResult, partialResponse)).thenReturn(restResponse);

    // Invoke.
    _noFilterRestLiCallback.onSuccess(result, executionReport);

    // Verify.
    verify(_responseHandler).buildPartialResponse(_routingResult, responseData);
    verify(_responseHandler).buildRestLiResponseData(_restRequest, _routingResult, result);
    verify(_responseHandler).buildResponse(_routingResult, partialResponse);
    verify(_callback).onSuccess(restResponse, executionReport);
    verifyZeroInteractions(_restRequest, _routingResult);
    verifyNoMoreInteractions(_responseHandler, _callback);
  }

  @Test
  public void testOnErrorRestExceptionNoFilters() throws Exception
  {
    RestException ex = new RestException(new RestResponseBuilder().build());
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    // Invoke.
    _noFilterRestLiCallback.onError(ex, executionReport);
    // Verify.
    verify(_callback).onError(ex, executionReport);
    verifyZeroInteractions(_responseHandler, _restRequest, _routingResult);
    verifyNoMoreInteractions(_callback);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testOnErrorRestLiServiceExceptionNoFilters() throws Exception
  {
    RestLiServiceException ex = new RestLiServiceException(HttpStatus.S_404_NOT_FOUND);
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    Map<String, String> inputHeaders = Maps.newHashMap();
    inputHeaders.put(RestConstants.HEADER_RESTLI_PROTOCOL_VERSION,
                     AllProtocolVersions.BASELINE_PROTOCOL_VERSION.toString());

    Map<String, String> restExceptionHeaders = Maps.newHashMap();
    restExceptionHeaders.put("foo", "bar");

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Map> augErrorHeadersCapture = ArgumentCaptor.forClass(Map.class);
    AugmentedRestLiResponseData responseData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(ex.getStatus()).headers(restExceptionHeaders).build();
    PartialRestResponse partialResponse = new PartialRestResponse.Builder().build();
    RestException restException = new RestException(new RestResponseBuilder().build());
    // Set up.
    when(_restRequest.getHeaders()).thenReturn(inputHeaders);
    when(
         _responseHandler.buildErrorResponseData(eq(_restRequest), eq(_routingResult), eq(ex),
                                                 augErrorHeadersCapture.capture())).thenReturn(responseData);
    when(_responseHandler.buildPartialResponse(_routingResult, responseData)).thenReturn(partialResponse);
    when(_responseHandler.buildRestException(ex, partialResponse)).thenReturn(restException);

    // Invoke.
    _noFilterRestLiCallback.onError(ex, executionReport);

    // Verify.
    verify(_responseHandler).buildRestException(ex, partialResponse);
    verify(_responseHandler).buildErrorResponseData(eq(_restRequest), eq(_routingResult), eq(ex),
                                                    augErrorHeadersCapture.capture());
    verify(_responseHandler).buildPartialResponse(_routingResult, responseData);
    verify(_callback).onError(restException, executionReport);
    verify(_restRequest, times(2)).getHeaders();
    verifyZeroInteractions(_routingResult);
    verifyNoMoreInteractions(_restRequest, _responseHandler, _callback);
    Map<String, String> augErrorHeaders = augErrorHeadersCapture.getValue();
    assertNotNull(augErrorHeaders);
    assertFalse(augErrorHeaders.isEmpty());
    assertTrue(augErrorHeaders.containsKey(RestConstants.HEADER_RESTLI_PROTOCOL_VERSION));
    assertEquals(augErrorHeaders.get(RestConstants.HEADER_RESTLI_PROTOCOL_VERSION),
                 AllProtocolVersions.BASELINE_PROTOCOL_VERSION.toString());
    String errorHeaderName = HeaderUtil.getErrorResponseHeaderName(inputHeaders);
    assertTrue(augErrorHeaders.containsKey(errorHeaderName));
    assertEquals(augErrorHeaders.get(errorHeaderName), RestConstants.HEADER_VALUE_ERROR);
  }

  @DataProvider(name = "provideExceptions")
  private Object[][] provideExceptions()
  {
    return new Object[][] { { new RuntimeException("Test runtime exception") },
        { new RoutingException("Test routing exception", 404) } };
  }

  @SuppressWarnings("unchecked")
  @Test(dataProvider = "provideExceptions")
  public void testOnErrorOtherExceptionNoFilters(Exception ex) throws Exception
  {
    ArgumentCaptor<RestLiServiceException> exCapture = ArgumentCaptor.forClass(RestLiServiceException.class);
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    PartialRestResponse partialResponse = new PartialRestResponse.Builder().build();
    AugmentedRestLiResponseData responseData = new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).build();
    RestException restException = new RestException(new RestResponseBuilder().build());
    Map<String, String> inputHeaders = Maps.newHashMap();
    inputHeaders.put(RestConstants.HEADER_RESTLI_PROTOCOL_VERSION, "2.0.0");

    // Set up.
    when(_restRequest.getHeaders()).thenReturn(inputHeaders);
    when(
         _responseHandler.buildErrorResponseData(eq(_restRequest), eq(_routingResult), exCapture.capture(),
                                                 anyMap())).thenReturn(responseData);
    when(_responseHandler.buildPartialResponse(_routingResult, responseData)).thenReturn(partialResponse);
    when(_responseHandler.buildRestException(ex, partialResponse)).thenReturn(restException);

    // Invoke.
    _noFilterRestLiCallback.onError(ex, executionReport);

    // Verify.
    verify(_responseHandler).buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                    exCapture.capture(), anyMap());
    verify(_responseHandler).buildPartialResponse(_routingResult, responseData);
    verify(_responseHandler).buildRestException(ex, partialResponse);
    verify(_callback).onError(restException, executionReport);
    verify(_restRequest, times(2)).getHeaders();
    verifyZeroInteractions(_routingResult);
    verifyNoMoreInteractions(_restRequest, _responseHandler, _callback);
    RestLiServiceException restliEx = exCapture.getValue();
    assertNotNull(restliEx);
    if (ex instanceof RoutingException)
    {
      assertEquals(HttpStatus.fromCode(((RoutingException) ex).getStatus()), restliEx.getStatus());
    }
    else
    {
      assertEquals(HttpStatus.S_500_INTERNAL_SERVER_ERROR, restliEx.getStatus());
    }
    assertEquals(ex.getMessage(), restliEx.getMessage());
    assertEquals(ex, restliEx.getCause());
  }

  @Test
  public void testOnSuccessWithFiltersSuccessful() throws Exception
  {
    String result = "foo";
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    final RecordTemplate entityFromApp = Foo.createFoo("Key", "One");
    final Map<String, String> headersFromApp = Maps.newHashMap();
    headersFromApp.put("Key", "Input");
    final RecordTemplate entityFromFilter1 = Foo.createFoo("Key", "Two");
    final RecordTemplate entityFromFilter2 = Foo.createFoo("Key", "Three");
    final Map<String, String> headersFromFilters = Maps.newHashMap();
    headersFromFilters.put("Key", "Output");
    AugmentedRestLiResponseData appResponseData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_200_OK).headers(headersFromApp)
                                                    .entity(entityFromApp).build();
    PartialRestResponse partialResponse = new PartialRestResponse.Builder().build();

    // Setup.
    when(_responseHandler.buildRestLiResponseData(_restRequest, _routingResult, result)).thenReturn(appResponseData);
    when(_responseHandler.buildPartialResponse(_routingResult, appResponseData)).thenReturn(partialResponse);
    // Mock the behavior of the first filter.
    doAnswer(new Answer<Object>()
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        // Verify incoming data.
        assertEquals(HttpStatus.S_200_OK, context.getHttpStatus());
        assertEquals(headersFromApp, context.getResponseHeaders());
        assertEquals(entityFromApp, context.getResponseData().getEntityResponse());
        // Modify data in filter.
        context.setHttpStatus(HttpStatus.S_400_BAD_REQUEST);
        context.getResponseData().setEntityResponse(entityFromFilter1);
        context.getResponseHeaders().clear();
        return null;
      }
    }).doAnswer(new Answer<Object>()
    // Mock the behavior of the first filter.
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        // Verify incoming data.
        assertEquals(HttpStatus.S_400_BAD_REQUEST, context.getHttpStatus());
        assertTrue(context.getResponseHeaders().isEmpty());
        assertEquals(context.getResponseData().getEntityResponse(), entityFromFilter1);
        // Modify data in filter.
        context.setHttpStatus(HttpStatus.S_403_FORBIDDEN);
        context.getResponseData().setEntityResponse(entityFromFilter2);
        context.getResponseHeaders().putAll(headersFromFilters);
        return null;
      }
    }).when(_filter).onResponse(eq(_filterRequestContext), any(FilterResponseContext.class));

    RestResponse restResponse = new RestResponseBuilder().build();
    when(_responseHandler.buildResponse(_routingResult, partialResponse)).thenReturn(restResponse);

    // Invoke.
    _twoFilterRestLiCallback.onSuccess(result, executionReport);

    // Verify.
    assertNotNull(appResponseData);
    assertEquals(HttpStatus.S_403_FORBIDDEN, appResponseData.getStatus());
    assertEquals(entityFromFilter2, appResponseData.getEntityResponse());
    assertEquals(headersFromFilters, appResponseData.getHeaders());
    verify(_responseHandler).buildRestLiResponseData(_restRequest, _routingResult, result);
    verify(_responseHandler).buildPartialResponse(_routingResult, appResponseData);
    verify(_responseHandler).buildResponse(_routingResult, partialResponse);
    verify(_callback).onSuccess(restResponse, executionReport);
    verifyZeroInteractions(_restRequest, _routingResult);
    verifyNoMoreInteractions(_responseHandler, _callback);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testOnSuccessWithFiltersExceptionFromFirstFilterSecondFilterHandlesEx() throws Exception
  {
    // App stuff.
    final RecordTemplate entityFromApp = Foo.createFoo("Key", "One");
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    AugmentedRestLiResponseData appResponseData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_200_OK).entity(entityFromApp)
                                                                   .build();

    // Filter suff.
    ArgumentCaptor<RestLiServiceException> exFromFilterCapture = ArgumentCaptor.forClass(RestLiServiceException.class);
    final Map<String, String> headersFromFilter = Maps.newHashMap();
    headersFromFilter.put("Key", "Error from filter");
    final ErrorResponse errorResponseFromFilter = new ErrorResponse().setStatus(500);
    AugmentedRestLiResponseData responseErrorData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_500_INTERNAL_SERVER_ERROR)
                                                                   .errorResponse(errorResponseFromFilter)
                                                                   .headers(headersFromFilter).build();
    final RecordTemplate entityFromFilter = Foo.createFoo("Key", "Two");
    PartialRestResponse partialFilterErrorResponse = new PartialRestResponse.Builder().build();
    final Exception exFromFilter = new RuntimeException("Exception From Filter");
    // Common stuff.
    RestResponse restResponse = new RestResponseBuilder().build();
    // Setup.
    when(_responseHandler.buildRestLiResponseData(_restRequest, _routingResult, entityFromApp)).thenReturn(appResponseData);
    when(_restRequest.getHeaders()).thenReturn(null);
    when(
         _responseHandler.buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                 exFromFilterCapture.capture(), anyMap())).thenReturn(responseErrorData);
    when(_responseHandler.buildPartialResponse(_routingResult, responseErrorData)).thenReturn(partialFilterErrorResponse);

    when(_responseHandler.buildResponse(_routingResult, partialFilterErrorResponse)).thenReturn(restResponse);
    // Mock filter behavior.
    doThrow(exFromFilter).doAnswer(new Answer<Object>()
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        // The second filter should be invoked with details of the exception thrown by the first
        // filter.
        assertEquals(context.getHttpStatus(), HttpStatus.S_500_INTERNAL_SERVER_ERROR);
        assertNull(context.getResponseData().getEntityResponse());
        assertEquals(context.getResponseHeaders(), headersFromFilter);
        assertEquals(context.getResponseData().getErrorResponse(), errorResponseFromFilter);

        // Modify data.
        context.setHttpStatus(HttpStatus.S_402_PAYMENT_REQUIRED);
        // The second filter handles the exception thrown by the first filter (i.e.) sets an entity
        // response in the response data.
        context.getResponseData().setEntityResponse(entityFromFilter);
        return null;
      }
    }).when(_filter).onResponse(eq(_filterRequestContext), any(FilterResponseContext.class));

    // Invoke.
    _twoFilterRestLiCallback.onSuccess(entityFromApp, executionReport);

    // Verify.
    verify(_responseHandler).buildRestLiResponseData(_restRequest, _routingResult, entityFromApp);
    verify(_responseHandler).buildPartialResponse(_routingResult, responseErrorData);
    verify(_responseHandler).buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                    exFromFilterCapture.capture(), anyMap());
    verify(_responseHandler).buildPartialResponse(_routingResult, responseErrorData);
    verify(_responseHandler).buildResponse(_routingResult, partialFilterErrorResponse);
    verify(_callback).onSuccess(restResponse, executionReport);
    verify(_restRequest, times(2)).getHeaders();
    verifyZeroInteractions(_routingResult);
    verifyNoMoreInteractions(_responseHandler, _callback);
    assertFalse(responseErrorData.isErrorResponse());
    assertEquals(responseErrorData.getEntityResponse(), entityFromFilter);
    RestLiServiceException restliEx = exFromFilterCapture.getValue();
    assertNotNull(restliEx);
    assertEquals(HttpStatus.S_500_INTERNAL_SERVER_ERROR, restliEx.getStatus());
    assertEquals(exFromFilter.getMessage(), restliEx.getMessage());
    assertEquals(exFromFilter, restliEx.getCause());
    assertNotNull(responseErrorData);
    assertEquals(HttpStatus.S_402_PAYMENT_REQUIRED, responseErrorData.getStatus());
    assertEquals(responseErrorData.getHeaders(), headersFromFilter);
    assertNull(responseErrorData.getErrorResponse());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testOnSuccessWithFiltersExceptionFromFirstFilterSecondFilterDoesNotHandleEx() throws Exception
  {
    // App stuff.
    final RecordTemplate entityFromApp = Foo.createFoo("Key", "Two");
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    AugmentedRestLiResponseData appResponseData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_200_OK).entity(entityFromApp)
                                                                   .build();

    // Filter suff.
    ArgumentCaptor<RestLiServiceException> exFromFilterCapture = ArgumentCaptor.forClass(RestLiServiceException.class);
    final Map<String, String> headersFromFilter = Maps.newHashMap();
    headersFromFilter.put("Key", "Error from filter");
    ErrorResponse errorResponseFromFilter = new ErrorResponse().setStatus(500);
    AugmentedRestLiResponseData responseErrorData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_500_INTERNAL_SERVER_ERROR)
                                                                   .errorResponse(errorResponseFromFilter)
                                                                   .headers(headersFromFilter).build();
    PartialRestResponse partialFilterErrorResponse = new PartialRestResponse.Builder().build();
    final Exception exFromFilter = new RuntimeException("Exception From Filter");

    // Common stuff.
    RestException finalRestException = new RestException(new RestResponseBuilder().build());
    // Setup.
    when(_responseHandler.buildRestLiResponseData(_restRequest, _routingResult, entityFromApp)).thenReturn(appResponseData);
    when(_restRequest.getHeaders()).thenReturn(null);
    when(
         _responseHandler.buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                 exFromFilterCapture.capture(), anyMap())).thenReturn(responseErrorData);
    when(_responseHandler.buildPartialResponse(_routingResult, responseErrorData)).thenReturn(partialFilterErrorResponse);
    when(_responseHandler.buildRestException(exFromFilter, partialFilterErrorResponse)).thenReturn(finalRestException);    // Mock filter behavior.
    doThrow(exFromFilter).doAnswer(new Answer<Object>()
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        // The second filter should be invoked with details of the exception thrown by the first
        // filter.
        assertEquals(context.getHttpStatus(), HttpStatus.S_500_INTERNAL_SERVER_ERROR);
        assertNull(context.getResponseData().getEntityResponse());
        assertEquals(context.getResponseHeaders(), headersFromFilter);

        // Modify data.
        context.setHttpStatus(HttpStatus.S_402_PAYMENT_REQUIRED);
        // The second filter handles the exception thrown by the first filter (i.e.) does not throw
        // another exception.
        return null;
      }
    }).when(_filter).onResponse(eq(_filterRequestContext), any(FilterResponseContext.class));

    // Invoke.
    _twoFilterRestLiCallback.onSuccess(entityFromApp, executionReport);

    // Verify.
    verify(_responseHandler).buildRestLiResponseData(_restRequest, _routingResult, entityFromApp);
    verify(_responseHandler).buildPartialResponse(_routingResult, responseErrorData);
    verify(_responseHandler).buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                    exFromFilterCapture.capture(), anyMap());
    verify(_responseHandler).buildPartialResponse(_routingResult, responseErrorData);
    verify(_responseHandler).buildRestException(exFromFilter, partialFilterErrorResponse);
    verify(_callback).onError(finalRestException, executionReport);
    verify(_restRequest, times(2)).getHeaders();
    verifyZeroInteractions(_routingResult);
    verifyNoMoreInteractions(_responseHandler, _callback);
    RestLiServiceException restliEx = exFromFilterCapture.getValue();
    assertNotNull(restliEx);
    assertEquals(HttpStatus.S_500_INTERNAL_SERVER_ERROR, restliEx.getStatus());
    assertEquals(exFromFilter.getMessage(), restliEx.getMessage());
    assertEquals(exFromFilter, restliEx.getCause());
    assertNotNull(responseErrorData);
    assertEquals(HttpStatus.S_402_PAYMENT_REQUIRED, responseErrorData.getStatus());
    assertEquals(responseErrorData.getHeaders(), headersFromFilter);
    assertNull(responseErrorData.getEntityResponse());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testOnSuccessWithFiltersExceptionFromSecondFilter() throws Exception
  {
    // App stuff.
    String result = "foo";
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    AugmentedRestLiResponseData appResponseData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_200_OK).build();

    // Filter suff.
    ArgumentCaptor<RestLiServiceException> exFromFilterCapture = ArgumentCaptor.forClass(RestLiServiceException.class);
    final Map<String, String> headersFromFilter = Maps.newHashMap();
    headersFromFilter.put("Key", "Error from filter");
    AugmentedRestLiResponseData filterResponseData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_500_INTERNAL_SERVER_ERROR)
                                                    .headers(headersFromFilter).build();
    PartialRestResponse partialFilterErrorResponse = new PartialRestResponse.Builder().build();
    final Exception exFromFilter = new RuntimeException("Excepiton From Filter");

    // Common stuff.
    RestException finalRestException = new RestException(new RestResponseBuilder().build());
    // Setup.
    when(_responseHandler.buildRestLiResponseData(_restRequest, _routingResult, result)).thenReturn(appResponseData);
    when(_restRequest.getHeaders()).thenReturn(null);
    when(
         _responseHandler.buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                 exFromFilterCapture.capture(), anyMap())).thenReturn(filterResponseData);
    when(_responseHandler.buildPartialResponse(_routingResult, filterResponseData)).thenReturn(partialFilterErrorResponse);
    when(_responseHandler.buildRestException(exFromFilter, partialFilterErrorResponse)).thenReturn(finalRestException);
    // Mock filter behavior.
    doAnswer(new Answer<Object>()
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        // The second filter should be invoked with details of the exception thrown by the first
        // filter. Verify incoming data.
        assertEquals(context.getHttpStatus(), HttpStatus.S_200_OK);
        assertNull(context.getResponseData().getEntityResponse());
        assertTrue(context.getResponseHeaders().isEmpty());
        // Modify data.
        context.setHttpStatus(HttpStatus.S_402_PAYMENT_REQUIRED);
        return null;
      }
    }).doThrow(exFromFilter).when(_filter).onResponse(eq(_filterRequestContext), any(FilterResponseContext.class));

    // Invoke.
    _twoFilterRestLiCallback.onSuccess(result, executionReport);

    // Verify.
    verify(_responseHandler).buildPartialResponse(_routingResult, filterResponseData);
    verify(_responseHandler).buildRestLiResponseData(_restRequest, _routingResult, result);
    verify(_responseHandler).buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                    exFromFilterCapture.capture(), anyMap());
    verify(_responseHandler).buildPartialResponse(_routingResult, filterResponseData);
    verify(_responseHandler).buildRestException(exFromFilter, partialFilterErrorResponse);
    verify(_callback).onError(finalRestException, executionReport);
    verify(_restRequest, times(2)).getHeaders();
    verifyZeroInteractions(_routingResult);
    verifyNoMoreInteractions(_responseHandler, _callback);
    RestLiServiceException restliEx = exFromFilterCapture.getValue();
    assertNotNull(restliEx);
    assertEquals(HttpStatus.S_500_INTERNAL_SERVER_ERROR, restliEx.getStatus());
    assertEquals(exFromFilter.getMessage(), restliEx.getMessage());
    assertEquals(exFromFilter, restliEx.getCause());
    assertNotNull(filterResponseData);
    assertEquals(HttpStatus.S_500_INTERNAL_SERVER_ERROR, filterResponseData.getStatus());
    assertEquals(filterResponseData.getHeaders(), headersFromFilter);
    assertNull(filterResponseData.getEntityResponse());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testOnErrorWithFiltersNotHandlingAppEx() throws Exception
  {
    Exception exFromApp = new RuntimeException("Runtime exception from app");
    ErrorResponse appErrorResponse = new ErrorResponse().setStatus(404);
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    final Map<String, String> headersFromApp = Maps.newHashMap();
    headersFromApp.put("Key", "Input");
    final Map<String, String> headersFromFilter = Maps.newHashMap();
    headersFromFilter.put("Key", "Output");

    AugmentedRestLiResponseData responseData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_404_NOT_FOUND)
                                                                   .headers(headersFromApp)
                                                                   .errorResponse(appErrorResponse).build();
    PartialRestResponse partialResponse = new PartialRestResponse.Builder().build();
    ArgumentCaptor<RestLiServiceException> exCapture = ArgumentCaptor.forClass(RestLiServiceException.class);
    when(
         _responseHandler.buildErrorResponseData(eq(_restRequest), eq(_routingResult), exCapture.capture(),
                                                 anyMap())).thenReturn(responseData);
    when(_responseHandler.buildPartialResponse(_routingResult, responseData)).thenReturn(partialResponse);

    // Mock the behavior of the first filter.
    doAnswer(new Answer<Object>()
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        // Verify incoming data.
        assertEquals(HttpStatus.S_404_NOT_FOUND, context.getHttpStatus());
        assertEquals(headersFromApp, context.getResponseHeaders());
        assertNull(context.getResponseData().getEntityResponse());
        // Modify data in filter.
        context.setHttpStatus(HttpStatus.S_400_BAD_REQUEST);
        context.getResponseHeaders().clear();
        return null;
      }
    }).doAnswer(new Answer<Object>()
    // Mock the behavior of the second filter.
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        // Verify incoming data.
        assertEquals(HttpStatus.S_400_BAD_REQUEST, context.getHttpStatus());
        assertTrue(context.getResponseHeaders().isEmpty());
        assertNull(context.getResponseData().getEntityResponse());
        // Modify data in filter.
        context.setHttpStatus(HttpStatus.S_403_FORBIDDEN);
        context.getResponseHeaders().putAll(headersFromFilter);
        return null;
      }
    }).when(_filter).onResponse(eq(_filterRequestContext), any(FilterResponseContext.class));
    RestException restException = new RestException(new RestResponseBuilder().build());
    when(_responseHandler.buildRestException(exFromApp, partialResponse)).thenReturn(restException);
    // Invoke.
    _twoFilterRestLiCallback.onError(exFromApp, executionReport);
    // Verify.
    assertNotNull(responseData);
    assertEquals(HttpStatus.S_403_FORBIDDEN, responseData.getStatus());
    assertNull(responseData.getEntityResponse());
    assertTrue(responseData.isErrorResponse());
    assertEquals(responseData.getErrorResponse(), appErrorResponse);
    assertEquals(headersFromFilter, responseData.getHeaders());
    verify(_responseHandler).buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                    exCapture.capture(), anyMap());
    verify(_responseHandler).buildPartialResponse(_routingResult, responseData);
    verify(_responseHandler).buildRestException(exFromApp, partialResponse);
    verify(_callback).onError(restException, executionReport);
    verify(_restRequest, times(2)).getHeaders();
    verifyZeroInteractions(_routingResult);
    verifyNoMoreInteractions(_restRequest, _responseHandler, _callback);
    RestLiServiceException restliEx = exCapture.getValue();
    assertNotNull(restliEx);
    assertEquals(HttpStatus.S_500_INTERNAL_SERVER_ERROR, restliEx.getStatus());
    assertEquals(exFromApp.getMessage(), restliEx.getMessage());
    assertEquals(exFromApp, restliEx.getCause());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testOnErrorWithFiltersSuccessfulyHandlingAppEx() throws Exception
  {
    Exception exFromApp = new RuntimeException("Runtime exception from app");
    ErrorResponse appErrorResponse = new ErrorResponse().setStatus(404);
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    final Map<String, String> headersFromApp = Maps.newHashMap();
    headersFromApp.put("Key", "Input");
    final RecordTemplate entityFromFilter = Foo.createFoo("Key", "Two");
    final Map<String, String> headersFromFilter = Maps.newHashMap();
    headersFromFilter.put("Key", "Output");

    AugmentedRestLiResponseData responseData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_404_NOT_FOUND)
                                                                   .headers(headersFromApp)
                                                                   .errorResponse(appErrorResponse).build();
    PartialRestResponse partialResponse = new PartialRestResponse.Builder().build();
    ArgumentCaptor<RestLiServiceException> exCapture = ArgumentCaptor.forClass(RestLiServiceException.class);
    when(
         _responseHandler.buildErrorResponseData(eq(_restRequest), eq(_routingResult), exCapture.capture(),
                                                 anyMap())).thenReturn(responseData);
    when(_responseHandler.buildPartialResponse(_routingResult, responseData)).thenReturn(partialResponse);

    // Mock the behavior of the first filter.
    doAnswer(new Answer<Object>()
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        // Verify incoming data.
        assertEquals(HttpStatus.S_404_NOT_FOUND, context.getHttpStatus());
        assertEquals(headersFromApp, context.getResponseHeaders());
        assertNull(context.getResponseData().getEntityResponse());
        // Modify data in filter.
        context.setHttpStatus(HttpStatus.S_400_BAD_REQUEST);
        context.getResponseHeaders().clear();
        return null;
      }
    }).doAnswer(new Answer<Object>()
    // Mock the behavior of the second filter.
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        // Verify incoming data.
        assertEquals(HttpStatus.S_400_BAD_REQUEST, context.getHttpStatus());
        assertTrue(context.getResponseHeaders().isEmpty());
        assertNull(context.getResponseData().getEntityResponse());
        // Modify data in filter.
        context.setHttpStatus(HttpStatus.S_403_FORBIDDEN);
        context.getResponseData().setEntityResponse(entityFromFilter);
        context.getResponseHeaders().putAll(headersFromFilter);
        return null;
      }
    }).when(_filter).onResponse(eq(_filterRequestContext), any(FilterResponseContext.class));

    RestResponse restResponse = new RestResponseBuilder().build();
    when(_responseHandler.buildResponse(_routingResult, partialResponse)).thenReturn(restResponse);


    // Invoke.
    _twoFilterRestLiCallback.onError(exFromApp, executionReport);
    // Verify.
    assertNotNull(responseData);
    assertEquals(HttpStatus.S_403_FORBIDDEN, responseData.getStatus());
    assertEquals(entityFromFilter, responseData.getEntityResponse());
    assertEquals(headersFromFilter, responseData.getHeaders());
    verify(_responseHandler).buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                    exCapture.capture(), anyMap());
    verify(_responseHandler).buildPartialResponse(_routingResult, responseData);
    verify(_responseHandler).buildResponse(_routingResult, partialResponse);
    verify(_callback).onSuccess(restResponse, executionReport);
    verify(_restRequest, times(2)).getHeaders();
    verifyZeroInteractions(_routingResult);
    verifyNoMoreInteractions(_restRequest, _responseHandler, _callback);
    RestLiServiceException restliEx = exCapture.getValue();
    assertNotNull(restliEx);
    assertEquals(HttpStatus.S_500_INTERNAL_SERVER_ERROR, restliEx.getStatus());
    assertEquals(exFromApp.getMessage(), restliEx.getMessage());
    assertEquals(exFromApp, restliEx.getCause());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testOnErrorWithFiltersExceptionFromFirstFilterSecondFilterDoesNotHandle() throws Exception
  {
    // App stuff.
    RestLiServiceException exFromApp = new RestLiServiceException(HttpStatus.S_404_NOT_FOUND, "App failure");
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    ErrorResponse appErrorResponse = new ErrorResponse().setStatus(404);
    AugmentedRestLiResponseData responseAppData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_404_NOT_FOUND)
                                                                   .errorResponse(appErrorResponse).build();

    // Filter stuff.
    final Exception exFromFirstFilter = new RuntimeException("Runtime exception from first filter");
    ErrorResponse filterErrorResponse = new ErrorResponse().setStatus(500);
    AugmentedRestLiResponseData responseFilterData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_500_INTERNAL_SERVER_ERROR)
                                                                   .errorResponse(filterErrorResponse).build();

    PartialRestResponse partialResponse = new PartialRestResponse.Builder().build();
    ArgumentCaptor<RestLiServiceException> wrappedExCapture = ArgumentCaptor.forClass(RestLiServiceException.class);
    RestException restException = new RestException(new RestResponseBuilder().build());

    // Setup.
    when(
         _responseHandler.buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                 wrappedExCapture.capture(), anyMap())).thenReturn(responseAppData)
                                                                                       .thenReturn(responseFilterData);
    when(_responseHandler.buildPartialResponse(_routingResult, responseFilterData)).thenReturn(partialResponse);
    when(_restRequest.getHeaders()).thenReturn(null);
    when(_responseHandler.buildRestException(exFromFirstFilter, partialResponse)).thenReturn(restException);

    // Mock filter behavior.
    doThrow(exFromFirstFilter).doAnswer(new Answer<Object>()
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        // The second filter should be invoked with details of the exception thrown by the first
        // filter. Verify incoming data.
        assertEquals(context.getHttpStatus(), HttpStatus.S_500_INTERNAL_SERVER_ERROR);
        assertNull(context.getResponseData().getEntityResponse());
        assertTrue(context.getResponseHeaders().isEmpty());
        assertTrue(context.getResponseData().isErrorResponse());

        // Modify data.
        context.setHttpStatus(HttpStatus.S_402_PAYMENT_REQUIRED);
        // The second filter does not handle the exception thrown by the first filter (i.e.) the
        // response data still has the error response corresponding to the exception from the first
        // filter.
        return null;
      }
    }).when(_filter).onResponse(eq(_filterRequestContext), any(FilterResponseContext.class));

    // Invoke.
    _twoFilterRestLiCallback.onError(exFromApp, executionReport);

    // Verify.
    verify(_responseHandler, times(2)).buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                              wrappedExCapture.capture(), anyMap());
    verify(_responseHandler).buildRestException(exFromFirstFilter, partialResponse);
    verify(_responseHandler).buildPartialResponse(_routingResult, responseFilterData);
    verify(_callback).onError(restException, executionReport);
    verify(_restRequest, times(4)).getHeaders();
    verifyZeroInteractions(_routingResult);
    verifyNoMoreInteractions(_restRequest, _responseHandler, _callback);
    assertNotNull(responseFilterData);
    assertEquals(HttpStatus.S_402_PAYMENT_REQUIRED, responseFilterData.getStatus());
    assertTrue(responseFilterData.getHeaders().isEmpty());
    assertNull(responseFilterData.getEntityResponse());
    RestLiServiceException restliEx = wrappedExCapture.getAllValues().get(0);
    assertNotNull(restliEx);
    assertEquals(exFromApp.getStatus(), restliEx.getStatus());
    assertEquals(exFromApp.getMessage(), restliEx.getMessage());
    restliEx = wrappedExCapture.getAllValues().get(1);
    assertNotNull(restliEx);
    assertEquals(HttpStatus.S_500_INTERNAL_SERVER_ERROR, restliEx.getStatus());
    assertEquals(exFromFirstFilter.getMessage(), restliEx.getMessage());
    assertEquals(exFromFirstFilter, restliEx.getCause());
  }


  @SuppressWarnings("unchecked")
  @Test
  public void testOnErrorWithFiltersExceptionFromFirstFilterSecondFilterHandles() throws Exception
  {
    // App stuff.
    RestLiServiceException exFromApp = new RestLiServiceException(HttpStatus.S_404_NOT_FOUND, "App failure");
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    ErrorResponse appErrorResponse = new ErrorResponse().setStatus(404);
    AugmentedRestLiResponseData responseAppData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_404_NOT_FOUND)
                                                                   .errorResponse(appErrorResponse).build();

    // Filter stuff.
    final Exception exFromFirstFilter = new RuntimeException("Runtime exception from first filter");
    ErrorResponse filterErrorResponse = new ErrorResponse().setStatus(500);
    AugmentedRestLiResponseData responseFilterData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_500_INTERNAL_SERVER_ERROR)
                                                                   .errorResponse(filterErrorResponse).build();
    final RecordTemplate entityFromFilter2 = Foo.createFoo("Key", "Two");

    PartialRestResponse partialResponse = new PartialRestResponse.Builder().build();
    ArgumentCaptor<RestLiServiceException> wrappedExCapture = ArgumentCaptor.forClass(RestLiServiceException.class);
    RestResponse restResponse = new RestResponseBuilder().build();


    // Setup.
    when(
         _responseHandler.buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                 wrappedExCapture.capture(), anyMap())).thenReturn(responseAppData)
                                                                                       .thenReturn(responseFilterData);
    when(_responseHandler.buildPartialResponse(_routingResult, responseFilterData)).thenReturn(partialResponse);
    when(_responseHandler.buildResponse(_routingResult, partialResponse)).thenReturn(restResponse);
    when(_restRequest.getHeaders()).thenReturn(null);


    // Mock filter behavior.
    doThrow(exFromFirstFilter).doAnswer(new Answer<Object>()
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        // The second filter should be invoked with details of the exception thrown by the first
        // filter. Verify incoming data.
        assertEquals(context.getHttpStatus(), HttpStatus.S_500_INTERNAL_SERVER_ERROR);
        assertNull(context.getResponseData().getEntityResponse());
        assertTrue(context.getResponseHeaders().isEmpty());
        assertTrue(context.getResponseData().isErrorResponse());

        // Modify data.
        context.setHttpStatus(HttpStatus.S_402_PAYMENT_REQUIRED);
        // The second filter does handles the exception thrown by the first filter (i.e.) clears the
        // error response corresponding to the exception from the first
        // filter.
        context.getResponseData().setEntityResponse(entityFromFilter2);
        return null;
      }
    }).when(_filter).onResponse(eq(_filterRequestContext), any(FilterResponseContext.class));

    // Invoke.
    _twoFilterRestLiCallback.onError(exFromApp, executionReport);

    // Verify.
    verify(_responseHandler, times(2)).buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                              wrappedExCapture.capture(), anyMap());
    verify(_responseHandler).buildPartialResponse(_routingResult, responseFilterData);
    verify(_responseHandler).buildResponse(_routingResult, partialResponse);
    verify(_callback).onSuccess(restResponse, executionReport);
    verify(_restRequest, times(4)).getHeaders();
    verifyZeroInteractions(_routingResult);
    verifyNoMoreInteractions(_restRequest, _responseHandler, _callback);
    assertNotNull(responseFilterData);
    assertEquals(HttpStatus.S_402_PAYMENT_REQUIRED, responseFilterData.getStatus());
    assertTrue(responseFilterData.getHeaders().isEmpty());
    assertEquals(responseFilterData.getEntityResponse(), entityFromFilter2);
    assertFalse(responseFilterData.isErrorResponse());
    RestLiServiceException restliEx = wrappedExCapture.getAllValues().get(0);
    assertNotNull(restliEx);
    assertEquals(exFromApp.getStatus(), restliEx.getStatus());
    assertEquals(exFromApp.getMessage(), restliEx.getMessage());
    restliEx = wrappedExCapture.getAllValues().get(1);
    assertNotNull(restliEx);
    assertEquals(HttpStatus.S_500_INTERNAL_SERVER_ERROR, restliEx.getStatus());
    assertEquals(exFromFirstFilter.getMessage(), restliEx.getMessage());
    assertEquals(exFromFirstFilter, restliEx.getCause());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testOnErrorWithFiltersExceptionFromSecondFilter() throws Exception
  {
    // App stuff.
    RestLiServiceException exFromApp = new RestLiServiceException(HttpStatus.S_404_NOT_FOUND, "App failure");
    RequestExecutionReport executionReport = new RequestExecutionReportBuilder().build();
    AugmentedRestLiResponseData responseAppData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_404_NOT_FOUND).build();
    // Filter stuff.
    final Exception exFromSecondFilter = new RuntimeException("Runtime exception from second filter");
    AugmentedRestLiResponseData responseFilterData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_500_INTERNAL_SERVER_ERROR).build();

    PartialRestResponse partialResponse = new PartialRestResponse.Builder().build();

    ArgumentCaptor<RestLiServiceException> wrappedExCapture = ArgumentCaptor.forClass(RestLiServiceException.class);
    RestException restException = new RestException(new RestResponseBuilder().build());

    // Setup.
    when(
         _responseHandler.buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                 wrappedExCapture.capture(), anyMap())).thenReturn(responseAppData)
                                                                                       .thenReturn(responseFilterData);
    when(_responseHandler.buildPartialResponse(_routingResult, responseFilterData)).thenReturn(partialResponse);
    when(_restRequest.getHeaders()).thenReturn(null);
    when(_responseHandler.buildRestException(exFromSecondFilter, partialResponse)).thenReturn(restException);

    // Mock filter behavior.
    doAnswer(new Answer<Object>()
    {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable
      {
        Object[] args = invocation.getArguments();
        FilterResponseContext context = (FilterResponseContext) args[1];
        assertEquals(context.getHttpStatus(), HttpStatus.S_404_NOT_FOUND);
        assertNull(context.getResponseData().getEntityResponse());
        assertTrue(context.getResponseHeaders().isEmpty());

        // Modify data.
        context.setHttpStatus(HttpStatus.S_402_PAYMENT_REQUIRED);
        return null;
      }
    }).doThrow(exFromSecondFilter).when(_filter)
      .onResponse(eq(_filterRequestContext), any(FilterResponseContext.class));

    // Invoke.
    _twoFilterRestLiCallback.onError(exFromApp, executionReport);

    // Verify.
    verify(_responseHandler, times(2)).buildErrorResponseData(eq(_restRequest), eq(_routingResult),
                                                              wrappedExCapture.capture(), anyMap());
    verify(_responseHandler).buildPartialResponse(_routingResult, responseFilterData);
    verify(_responseHandler).buildRestException(exFromSecondFilter, partialResponse);
    verify(_callback).onError(restException, executionReport);
    verify(_restRequest, times(4)).getHeaders();
    verifyZeroInteractions(_routingResult);
    verifyNoMoreInteractions(_restRequest, _responseHandler, _callback);
    assertNotNull(responseFilterData);
    assertEquals(HttpStatus.S_500_INTERNAL_SERVER_ERROR, responseFilterData.getStatus());
    assertTrue(responseFilterData.getHeaders().isEmpty());
    assertNull(responseFilterData.getEntityResponse());
    RestLiServiceException restliEx = wrappedExCapture.getAllValues().get(0);
    assertNotNull(restliEx);
    assertEquals(exFromApp.getStatus(), restliEx.getStatus());
    assertEquals(exFromApp.getMessage(), restliEx.getMessage());
    restliEx = wrappedExCapture.getAllValues().get(1);
    assertNotNull(restliEx);
    assertEquals(HttpStatus.S_500_INTERNAL_SERVER_ERROR, restliEx.getStatus());
    assertEquals(exFromSecondFilter.getMessage(), restliEx.getMessage());
    assertEquals(exFromSecondFilter, restliEx.getCause());
  }

  @Test
  public void testFilterResponseContextAdapter() throws RestLiResponseDataException
  {
    DataMap dataMap = new DataMap();
    dataMap.put("foo", "bar");
    Map<String, String> headers = Maps.newHashMap();
    headers.put("x", "y");
    RecordTemplate entity1 = new Foo(dataMap);
    AugmentedRestLiResponseData responseData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).status(HttpStatus.S_200_OK).headers(headers).entity(entity1)
                                                    .build();
    AugmentedRestLiResponseData updatedResponseData =
        new AugmentedRestLiResponseData.Builder(ResourceMethod.GET).build();
    FilterResponseContext context = new RestLiCallback.FilterResponseContextAdapter(responseData);
    assertEquals(headers, context.getResponseHeaders());
    assertEquals(entity1, context.getResponseData().getEntityResponse());
    assertEquals(HttpStatus.S_200_OK, context.getHttpStatus());

    context.setHttpStatus(HttpStatus.S_404_NOT_FOUND);
    Foo entity2 = Foo.createFoo("boo", "bar");
    context.getResponseData().setEntityResponse(entity2);
    assertEquals(context.getResponseData().getEntityResponse(), entity2);
    assertEquals(HttpStatus.S_404_NOT_FOUND, context.getHttpStatus());
    assertEquals(HttpStatus.S_404_NOT_FOUND, responseData.getStatus());
    assertEquals(responseData, context.getResponseData());
    assertEquals(responseData, ((FilterResponseContextInternal) context).getAugmentedRestLiResponseData());
    ((FilterResponseContextInternal) context).setAugmentedRestLiResponseData(updatedResponseData);
    assertEquals(updatedResponseData, ((FilterResponseContextInternal) context).getAugmentedRestLiResponseData());
  }

  private static class Foo extends RecordTemplate
  {
    private Foo(DataMap map)
    {
      super(map, null);
    }

    public static Foo createFoo(String key, String value)
    {
      DataMap dataMap = new DataMap();
      dataMap.put(key, value);
      return new Foo(dataMap);
    }
  }
}
