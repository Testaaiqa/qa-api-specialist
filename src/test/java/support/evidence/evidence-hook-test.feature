@id=EVIDENCE-HOOK-TEST
Feature: Evidence hook capture

  Scenario: Capture an actual GET exchange from Karate
    Given url baseUrl
    And path 'evidence'
    When method get
    Then status 200
    And match response.message == 'captured'
