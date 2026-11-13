@users
Feature: Cadastro de usuários

  Background:
    * url baseUrl
    * def UserTestData = Java.type('support.evidence.UserTestData')
    * def testEmail = UserTestData.validEmail()
    * def testPassword = UserTestData.password()
    * configure headers = { Accept: 'application/json' }

  @id=USR-POST-001 @priority=Alta @type=Contrato-Negativo
  Scenario: Rejeitar cadastro sem o campo nome
    * def payload = { email: '#(testEmail)', password: '#(testPassword)' }
    Given path 'users'
    And request payload
    When method post
    Then status 400

  @id=USR-POST-002 @priority=Alta @type=Contrato-Negativo
  Scenario: Rejeitar cadastro sem o campo e-mail
    * def payload = { name: 'Usuário Sintético de QA', password: '#(testPassword)' }
    Given path 'users'
    And request payload
    When method post
    Then status 400

  @id=USR-POST-003 @priority=Alta @type=Contrato-Negativo
  Scenario: Rejeitar cadastro sem o campo senha
    * def payload = { name: 'Usuário Sintético de QA', email: '#(testEmail)' }
    Given path 'users'
    And request payload
    When method post
    Then status 400

  @id=USR-POST-004 @priority=Alta @type=Contrato-Negativo
  Scenario: Rejeitar cadastro com objeto JSON vazio
    * def payload = {}
    Given path 'users'
    And request payload
    When method post
    Then status 400

  @id=USR-POST-005 @priority=Alta @type=Contrato-Negativo
  Scenario: Rejeitar senha com menos de seis caracteres
    * def payload = { name: 'Usuário Sintético de QA', email: '#(testEmail)', password: 'curta' }
    Given path 'users'
    And request payload
    When method post
    Then status 400

  @id=USR-POST-006 @priority=Média @type=Exploratório
  Scenario: Rejeitar e-mail em formato inválido
    * def payload = { name: 'Usuário Sintético de QA', email: '#(UserTestData.malformedEmail())', password: '#(testPassword)' }
    Given path 'users'
    And request payload
    When method post
    Then status 400

  @id=USR-POST-007 @priority=Média @type=Exploratório
  Scenario: Rejeitar e-mail vazio
    * def payload = { name: 'Usuário Sintético de QA', email: '', password: '#(testPassword)' }
    Given path 'users'
    And request payload
    When method post
    Then status 400

  @id=USR-POST-008 @priority=Alta @type=Smoke-Contrato
  Scenario: Cadastrar usuário com dados válidos
    * def payload = { name: 'Usuário Sintético de QA', email: '#(testEmail)', password: '#(testPassword)' }
    Given path 'users'
    And request payload
    When method post
    Then status 201

  @id=USR-POST-009 @priority=Alta @type=Contrato-Negativo
  Scenario: Rejeitar cadastro com e-mail duplicado
    * def payload = { name: 'Usuário Sintético de QA', email: '#(testEmail)', password: '#(testPassword)' }
    Given path 'users'
    And request payload
    When method post
    Then status 409
