@users @auth @login @sensitive-response
Feature: Autenticação de usuário

  Background:
    * url baseUrl
    * configure headers = { Accept: 'application/json' }
    * if (!syntheticData) karate.abort()
    * def setup = callonce read('classpath:support/evidence/create-login-user.feature')
    * def user = setup.account
    * def unknownEmail = user.email.replace('@', '.nao-cadastrado@')
    * def UserTestData = Java.type('support.evidence.UserTestData')
    * def malformedEmail = UserTestData.malformedEmail()
    * def incorrectPassword = user.password + '-incorreta'
    * def buildPayload =
      """
      function (caseId, account, unknown, malformed, wrongPassword) {
        switch (caseId) {
          case 'missingEmail': return { password: account.password };
          case 'missingPassword': return { email: account.email };
          case 'emptyBody': return {};
          case 'malformedEmail': return { email: malformed, password: account.password };
          case 'emptyEmail': return { email: '', password: account.password };
          case 'emptyPassword': return { email: account.email, password: '' };
          case 'unknownEmail': return { email: unknown, password: account.password };
          case 'incorrectPassword': return { email: account.email, password: wrongPassword };
          default: throw 'Caso de login desconhecido: ' + caseId;
        }
      }
      """
    * def containsJwt =
      """
      function (value) {
        var pattern = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
        function visit(item) {
          if (typeof item === 'string') {
            return pattern.test(item.replace(/^Bearer\s+/i, ''));
          }
          if (Array.isArray(item)) {
            return item.some(visit);
          }
          if (item && typeof item === 'object') {
            return Object.keys(item).some(function (key) { return visit(item[key]); });
          }
          return false;
        }
        return visit(value);
      }
      """

  @id=AUTH-LOGIN-001 @priority=Alta @type=Smoke-Contrato
  Scenario: Autenticar o usuário recém-criado e receber um token JWT
    * def payload = { email: '#(user.email)', password: '#(user.password)' }
    Given path 'auth', 'login'
    And request payload
    When method post
    Then status 200
    And assert containsJwt(response)

  @auth @login @negativo
  Scenario Outline: Rejeitar login para <caso> (esperado HTTP <statusEsperado>)
    * def payload = buildPayload(caseId, user, unknownEmail, malformedEmail, incorrectPassword)
    Given path 'auth', 'login'
    And request payload
    When method post
    Then status <statusEsperado>

    @id=AUTH-LOGIN-002 @priority=Alta @type=Exploratório
    Examples: campo e-mail ausente
      | caso               | caseId       | statusEsperado |
      | e-mail ausente     | missingEmail | 400            |

    @id=AUTH-LOGIN-003 @priority=Alta @type=Exploratório
    Examples: campo senha ausente
      | caso                 | caseId          | statusEsperado |
      | senha ausente        | missingPassword | 400            |

    @id=AUTH-LOGIN-004 @priority=Alta @type=Exploratório
    Examples: corpo vazio
      | caso                       | caseId   | statusEsperado |
      | corpo JSON vazio           | emptyBody | 400           |

    @id=AUTH-LOGIN-005 @priority=Média @type=Exploratório
    Examples: e-mail malformado
      | caso                       | caseId       | statusEsperado |
      | e-mail malformado          | malformedEmail | 400          |

    @id=AUTH-LOGIN-006 @priority=Média @type=Exploratório
    Examples: e-mail vazio
      | caso                       | caseId    | statusEsperado |
      | e-mail vazio               | emptyEmail | 400           |

    @id=AUTH-LOGIN-007 @priority=Média @type=Exploratório
    Examples: senha vazia
      | caso                       | caseId       | statusEsperado |
      | senha vazia                | emptyPassword | 400          |

    @id=AUTH-LOGIN-008 @priority=Alta @type=Contrato-Negativo
    Examples: usuário inexistente
      | caso                       | caseId      | statusEsperado |
      | usuário não cadastrado     | unknownEmail | 401           |

    @id=AUTH-LOGIN-009 @priority=Alta @type=Contrato-Negativo
    Examples: senha incorreta
      | caso                       | caseId          | statusEsperado |
      | senha incorreta            | incorrectPassword | 401         |
