@users @read-only @requires-auth @sensitive-response @id=USR-GET-001 @priority=High @type=Smoke-Contract
Feature: List users

  Background:
    * url baseUrl

  Scenario: Return the registered users
    * if (!apiToken || !syntheticData) karate.abort()
    Given path 'users'
    When method get
    Then status 200
    And match response != null
