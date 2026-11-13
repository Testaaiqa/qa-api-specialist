@users @read-only @requires-auth @requires-missing-id @id=USR-GET-002 @priority=Medium @type=Negative-Contract
Feature: Get a user by ID

  Background:
    * url baseUrl

  Scenario: Return not found for a confirmed missing user ID
    * def missingId = java.lang.System.getenv('API_MISSING_USER_ID')
    * if (!apiToken || !missingId) karate.abort()
    * def userId = Number(missingId)
    * assert !isNaN(userId)
    Given path 'users', userId
    When method get
    Then status 404
