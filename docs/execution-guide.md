# Guia de execução

## Pré-requisitos

- JDK 17 ou superior.
- Maven 3.8 ou superior.
- URL de ambiente autorizada e token de teste, quando aplicável.

O projeto não inclui credenciais. A URL `dev` disponibilizada pelo Swagger aponta para produção; por isso, a configuração exige `API_ALLOW_PRODUCTION=true` para executar contra ela. Prefira fornecer uma URL QA/staging autorizada em `API_BASE_URL`.

## Configuração no PowerShell

```powershell
$env:API_BASE_URL = 'https://<ambiente-autorizado>'
$env:API_TOKEN = '<token-de-teste>'
$env:API_TEST_DATA_SYNTHETIC = 'true'
$env:QA_REVEAL_SYNTHETIC_DATA = 'true'
# Opcional: exibe a senha sintética no payload da evidência.
# $env:QA_REVEAL_SYNTHETIC_PASSWORDS = 'true'
$env:QA_TEST_EMAIL_DOMAIN = '<dominio-de-teste-aprovado>'
$env:API_MISSING_USER_ID = '<id-numerico-confirmado-como-inexistente>'
$env:QA_DEMAND_ID = 'DEMANDA-001'
$env:QA_ANALYST_NAME = '<nome-do-analista>'
```

`QA_DEMAND_ID` e `QA_ANALYST_NAME` são necessários para as evidências. Se estiverem ausentes em uma execução com console interativo, o gerador solicitará os valores antes de produzir os arquivos. Em execução CI não interativa, informe-os como variáveis de ambiente; sem isso, a geração falha explicitamente.

O contexto adicional pode ser preenchido sem alterar o código, por exemplo com `QA_OBJECTIVE`, `QA_DEMAND_ORIGIN`, `QA_FUNCTIONAL_DESCRIPTION`, `QA_BUSINESS_RULES`, `QA_ACCEPTANCE_CRITERIA`, `QA_DEPENDENCIES`, `QA_TEST_SCOPE` e `QA_OUT_OF_SCOPE`. Campos não informados são identificados como tal no relatório.

`QA_TEST_EMAIL_DOMAIN` fornece o domínio aprovado para gerar um endereço aleatório exclusivo em cada execução. É obrigatório para executar a feature de criação, para não presumir que um domínio reservado seja aceito pela API.

Para a URL de produção documentada, somente após autorização expressa:

```powershell
$env:API_BASE_URL = 'https://projetinho-nu-two.vercel.app'
$env:API_ALLOW_PRODUCTION = 'true'
```

Não habilite `API_TEST_DATA_SYNTHETIC` se a listagem puder conter dados reais. O relatório de evidências guarda a resposta com o mascaramento aplicado, mas ainda deve ser tratado como dado sensível.

## Comandos

```powershell
mvn test
mvn test "-Dkarate.options=--tags @users"
mvn test "-Dkarate.options=--tags @read-only"
mvn test "-Dkarate.options=classpath:features/users/list-users.feature"
```

`mvn test` executa os cenários encontrados. Cenários sem token, sem confirmação de dados sintéticos ou sem ID configurado são abortados e aparecem como não executados/skipped, nunca como aprovados.

Para executar somente os cenários de criação de usuário:

```powershell
mvn test "-Dqa.features.path=classpath:features/users/cadastro-usuario.feature"
```

Para executar o fluxo de login e suas negativas:

```powershell
mvn test "-Dqa.features.path=classpath:features/auth/login.feature"
mvn test "-Dkarate.options=--tags @login"
```

O `POST /users` não declara autenticação no Swagger. A feature de criação substitui os headers globais e envia apenas `Accept: application/json`, para testar o cadastro sem Bearer. Ela gera um e-mail aleatório no domínio configurado em `QA_TEST_EMAIL_DOMAIN`. A limpeza via `DELETE /users/{id}` não faz parte dessa feature e exige token e ID confiável.

A feature de login cria uma conta sintética nova uma vez por execução com `callonce`; um cenário outline reutilizável cobre as tentativas negativas sem repetir a lógica da requisição. Os casos com campos ausentes, vazios ou malformados observaram HTTP 400, embora esses códigos não estejam documentados no Swagger. Usuário inexistente e senha incorreta são verificados contra HTTP 401. O sucesso valida HTTP 200 e a presença de um JWT; o valor do token continua mascarado.

## Evidências e relatórios

Cada execução gera `evidencia-qa.pdf`, `evidencia-qa.html` e `execution-data.json` em `reports/<QA_DEMAND_ID>/<AAAA-MM-DD>/<EXEC-ID>/`, além das pastas `screenshots/` e `attachments/`. O identificador aleatório exclusivo preserva execuções anteriores. `QA_REPORTS_DIR` pode alterar a raiz.

Os dados da API são capturados pelo hook `afterScenario` do Karate. Dados pessoais sintéticos podem ser exibidos na evidência somente quando `API_TEST_DATA_SYNTHETIC=true` e `QA_REVEAL_SYNTHETIC_DATA=true`; por padrão, continuam mascarados. A senha sintética enviada na requisição só é exibida se, além dessas duas variáveis, `QA_REVEAL_SYNTHETIC_PASSWORDS=true`. Tokens, cookies, chaves e outros segredos permanecem sempre mascarados; senhas também são sempre mascaradas nos logs HTTP. `QA_SENSITIVE_FIELDS` permite mascarar campos adicionais. O HTML nativo do Karate é desativado para evitar cópias sem o controle central de mascaramento.

As features mantêm as palavras-chave executáveis aceitas pelo parser do Karate; o capturador localiza o BDD apresentado nas evidências para português do Brasil.

Para manter a rastreabilidade direta, estruture cada cenário com uma chamada HTTP de negócio. O hook captura a última requisição/resposta realizada dentro de cada cenário; fluxos com múltiplas chamadas devem ser separados em cenários independentes quando isso não comprometer o isolamento do teste. O diretório `target/` não é versionado.

## Ambientes

- `dev`: URL de produção fornecida no Swagger, protegida pela confirmação explícita acima.
- `qa` e `staging`: sem URLs inventadas; configure `API_BASE_URL`.
- `-Dkarate.env=dev|qa|staging` seleciona o ambiente. `-Dapi.base.url` é um override explícito de execução; na ausência dele, `API_BASE_URL` prevalece para qualquer ambiente.
