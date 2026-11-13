function fn() {
  var env = karate.env || 'dev';
  var productionUrl = 'https://projetinho-nu-two.vercel.app';
  var urls = {
    dev: productionUrl,
    qa: null,
    staging: null
  };
  var baseUrl = karate.properties['api.base.url']
    || java.lang.System.getenv('API_BASE_URL')
    || urls[env];

  if (!baseUrl) {
    throw new Error('No base URL is configured for environment "' + env
      + '". Set API_BASE_URL before running API tests.');
  }

  baseUrl = baseUrl.replace(/\/+$/, '');
  var allowProduction = java.lang.System.getenv('API_ALLOW_PRODUCTION') === 'true';
  var isProduction = /^https:\/\/projetinho-nu-two\.vercel\.app(?:\/|$)/i.test(baseUrl);
  if (isProduction && !allowProduction) {
    throw new Error('The configured URL is production. Set API_ALLOW_PRODUCTION=true only after confirming authorization.');
  }

  var apiToken = java.lang.System.getenv('API_TOKEN');
  var syntheticData = java.lang.System.getenv('API_TEST_DATA_SYNTHETIC') === 'true';

  karate.configure('connectTimeout', 10000);
  karate.configure('readTimeout', 10000);
  var EvidenceLogModifier = Java.type('support.evidence.EvidenceLogModifier');
  karate.configure('logModifier', new EvidenceLogModifier());
  karate.configure('headers', function() {
    var headers = { Accept: 'application/json' };
    if (apiToken) {
      headers.Authorization = 'Bearer ' + apiToken;
    }
    return headers;
  });
  karate.configure('afterScenario', function() {
    var scenario = karate.scenario || {};
    var info = karate.info || {};
    var previous = karate.prevRequest || {};
    var tags = karate.tags || scenario.tags || info.tags || [];
    var status = karate.get('responseStatus');
    var blockedReason = karate.get('qaBlockedReason') || '';

    if (status == null) {
      var tagText = JSON.stringify(tags);
      if (tagText.indexOf('requires-auth') >= 0 && !apiToken) {
        blockedReason = 'API_TOKEN não configurado';
      } else if (tagText.indexOf('sensitive-response') >= 0 && !syntheticData) {
        blockedReason = 'Dados sintéticos não confirmados para resposta potencialmente sensível';
      } else if (tagText.indexOf('requires-missing-id') >= 0
          && !java.lang.System.getenv('API_MISSING_USER_ID')) {
        blockedReason = 'API_MISSING_USER_ID não configurado';
      }
    }

    var requestHeaders = previous.headers || {};
    var requestBody = typeof previous.body !== 'undefined' ? previous.body : karate.get('request');
    var requestUrl = previous.uri || previous.url || previous.fullUrl || '';
    var requestMethod = previous.method || previous.methodName || '';
    var responseHeaders = karate.get('responseHeaders');
    var responseBody = karate.get('response');
    var responseTime = karate.get('responseTime');

    var capture = {
      scenarioName: scenario.name || info.scenarioName || 'Cenário sem nome',
      scenarioDescription: info.scenarioDescription || '',
      featureName: info.featureName || '',
      featureFile: info.featureDir && info.featureFileName
        ? info.featureDir + '/' + info.featureFileName
        : (info.featureFileName || ''),
      tags: tags,
      environment: env,
      errorMessage: info.errorMessage || '',
      blockedReason: blockedReason,
      request: {
        method: requestMethod,
        url: requestUrl,
        headers: requestHeaders,
        contentType: (requestHeaders['Content-Type'] || requestHeaders['content-type'] || ''),
        payload: requestBody,
        correlationId: requestHeaders['X-Correlation-ID'] || requestHeaders['x-correlation-id'] || ''
      },
      response: {
        status: status,
        headers: responseHeaders || {},
        body: responseBody,
        durationMs: responseTime,
        requestTimestamp: karate.get('requestTimeStamp')
      }
    };

    var EvidenceCollector = Java.type('support.evidence.EvidenceCollector');
    EvidenceCollector.capture(JSON.stringify(capture));
  });

  return {
    env: env,
    baseUrl: baseUrl,
    apiToken: apiToken,
    syntheticData: syntheticData
  };
}
