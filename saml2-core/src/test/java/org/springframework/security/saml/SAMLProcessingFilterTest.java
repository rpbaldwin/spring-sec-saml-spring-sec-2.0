/* Copyright 2009 Vladimir Schäfer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.security.saml;

import org.easymock.Capture;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;
import org.opensaml.common.SAMLException;
import org.opensaml.common.xml.SAMLConstants;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.security.AuthenticationManager;
import org.springframework.security.AuthenticationServiceException;
import org.springframework.security.providers.UsernamePasswordAuthenticationToken;
import org.springframework.security.Authentication;
import org.springframework.security.saml.context.SAMLMessageContext;
import org.springframework.security.saml.processor.SAMLProcessor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import static junit.framework.Assert.assertEquals;
import static org.easymock.EasyMock.*;

/**
 * @author Vladimir Schafer
 */
public class SAMLProcessingFilterTest {

    ApplicationContext context;
    SAMLProcessingFilter processingFiler;
    SAMLProcessor processor;

    HttpServletRequest request;
    HttpSession session;

    @Before
    public void initialize() throws Exception {

        String resName = "/" + getClass().getName().replace('.', '/') + ".xml";
        context = new ClassPathXmlApplicationContext(resName);

        processingFiler = context.getBean("samlProcessingFilter", SAMLProcessingFilter.class);
        processor = createMock(SAMLProcessor.class);
        processingFiler.setSAMLProcessor(processor);

        request = createNiceMock(HttpServletRequest.class);
        session = createNiceMock(HttpSession.class);

    }

    /**
     * Verifies that the SAMLProcessor collaborator must be set, otherwise
     * exception is thrown
     */
    @Test(expected = IllegalArgumentException.class)
    public void testMissingProcessor() {
        expect(request.getContextPath()).andReturn("/saml");
        processingFiler.setSAMLProcessor(null);
    }

    /**
     * Verifies that error during processing results in error of whole
     * authentication.
     *
     * @throws Exception error
     */
    @Test(expected = AuthenticationServiceException.class)
    public void testErrorDuringProcessing() throws Exception {
        expect(request.getSession(true)).andReturn(session);
        expect(session.getAttribute("_springSamlStorageKey")).andReturn(null);
        expectLastCall().times(2);
        SAMLTestHelper.setLocalContextParameters(request, "/saml", null);
        expect(processor.retrieveMessage((SAMLMessageContext) notNull())).andThrow(new SAMLException("Processing error"));
        replayMock();
        processingFiler.attemptAuthentication(request);
        verifyMock();
    }

    @Test
    public void testDefaultURL() {
        assertEquals("/saml/SSO", processingFiler.getFilterProcessesUrl());
    }

    /**
     * Verifies that endpoint check fails when message is received using
     * unsupported binding.
     *
     * @throws Exception error
     */
    @Test(expected = AuthenticationServiceException.class)
    public void testInvalidBinding() throws Exception {
        expect(request.getSession(true)).andReturn(session);
        expect(session.getAttribute("_springSamlStorageKey")).andReturn(null);
        expectLastCall().times(2);

        AuthenticationManager manager = createMock(AuthenticationManager.class);
        processingFiler.setAuthenticationManager(manager);

        SAMLTestHelper.setLocalContextParameters(request, "/saml", null);
        final Capture<SAMLMessageContext> context = new Capture<SAMLMessageContext>();
        expect(processor.retrieveMessage(capture(context))).andAnswer(new IAnswer<SAMLMessageContext>() {
            public SAMLMessageContext answer() throws Throwable {
                context.getValue().setInboundSAMLBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
                return context.getValue();
            }
        });

        replay(manager);
        replayMock();
        processingFiler.attemptAuthentication(request);
        verifyMock();
        verify(manager);

    }

    /**
     * Verifies correct pass through the processing filter - process the
     * request, create SAML authentication token and pass it to the
     * authentication manager.
     *
     * @throws Exception error
     */
    @Test
    public void testCorrectPass() throws Exception {
        expect(request.getSession(true)).andReturn(session);
        expect(session.getAttribute("_springSamlStorageKey")).andReturn(null);
        expectLastCall().times(2);
        //expect(session.setAttribute("_springSamlStorageKey", anyObject())).andReturn(null);

        Authentication token = new UsernamePasswordAuthenticationToken("user", "pass");
        AuthenticationManager manager = createMock(AuthenticationManager.class);
        processingFiler.setAuthenticationManager(manager);

        SAMLTestHelper.setLocalContextParameters(request, "/saml", null);

        final Capture<SAMLMessageContext> context = new Capture<SAMLMessageContext>();
        expect(processor.retrieveMessage(capture(context))).andAnswer(new IAnswer<SAMLMessageContext>() {
            public SAMLMessageContext answer() throws Throwable {
                context.getValue().setInboundSAMLBinding(org.opensaml.common.xml.SAMLConstants.SAML2_POST_BINDING_URI);
                return context.getValue();
            }
        });
        expect(manager.authenticate((Authentication) notNull())).andReturn(token);

        replay(manager);
        replayMock();
        //verifyMock();
        Authentication authentication = processingFiler.attemptAuthentication(request);
        assertEquals(token, authentication);
        verifyMock();
        verify(manager);

    }

    private void replayMock() {
        replay(session);
        replay(processor);
        replay(request);
    }

    private void verifyMock() {
        verify(request);
        verify(processor);
        verify(session);
    }
}
