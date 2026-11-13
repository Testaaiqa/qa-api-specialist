@users @auth @sensitive-response
Feature: Preparação reutilizável de usuário sintético para autenticação

  @id=AUTH-SETUP-001 @priority=Alta @type=Preparação
  Scenario: Criar usuário novo para os cenários de login
    * configure headers = { Accept: 'application/json' }
    * def UserTestData = Java.type('support.evidence.UserTestData')
    * def email = UserTestData.validEmail()
    * def password = UserTestData.password()
    * def name = 'Usuário Sintético de QA'
    * def payload = { name: '#(name)', email: '#(email)', password: '#(password)' }
    Given url baseUrl
    And path 'users'
    And request payload
    When method post
    Then status 201
    * def account = { name: '#(name)', email: '#(email)', password: '#(password)' }
    * def EvidenceCollector = Java.type('support.evidence.EvidenceCollector')
    * def recordSetupEvidence =
      """
      function (collector, payload, apiUrl, environment, status, responseHeaders, responseBody, duration, requestTimestamp, requestHeaders) {
        var capture = {
          scenarioName: 'Criar usuário novo para os cenários de login',
          scenarioDescription: 'Preparar uma conta sintética nova para os testes de autenticação.',
          featureFile: 'src/test/java/support/evidence/create-login-user.feature',
          tags: ['@id=AUTH-SETUP-001', '@priority=Alta', '@type=Preparação', '@users', '@auth', '@sensitive-response'],
          environment: environment,
          request: {
            method: 'POST',
            url: apiUrl + '/users',
            headers: requestHeaders,
            contentType: requestHeaders['Content-Type'] || requestHeaders['content-type'] || 'application/json',
            payload: payload
          },
          response: {
            status: status,
            headers: responseHeaders || {},
            body: responseBody,
            durationMs: duration,
            requestTimestamp: requestTimestamp
          }
        };
        collector.capture(JSON.stringify(capture));
      }
      """
    * eval recordSetupEvidence(EvidenceCollector, payload, baseUrl, karate.env, responseStatus, responseHeaders, response, responseTime, karate.get('requestTimeStamp'), karate.prevRequest.headers)
