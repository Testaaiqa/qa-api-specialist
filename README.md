# QA API Specialist — Karate DSL

Projeto de automação de testes REST com Java 17, Maven e Karate DSL, baseado exclusivamente na especificação OpenAPI publicada no [Swagger](https://projetinho-nu-two.vercel.app/api-docs).

## Estrutura

- `src/test/java/runners`: runner JUnit 5 do Karate.
- `src/test/java/features/users`: cenários iniciais de leitura.
- `src/test/java/karate-config.js`: ambiente, URL, headers Bearer e timeouts.
- `src/test/java/support/evidence`: captura automática, mascaramento e geração de evidências.
- `docs/`: análise Swagger, inventário, estratégia e execução.
- `.github/agents/qa-specialist.agent.md`: instruções para demandas futuras de QA.

## Requisitos

- JDK 17+
- Maven 3.8+

## Configuração e execução

Use uma URL autorizada, token de teste e dados sintéticos. Veja [docs/execution-guide.md](docs/execution-guide.md) para os comandos PowerShell, ambientes, tags e execução de cenário individual.

```powershell
$env:API_BASE_URL = 'https://<ambiente-autorizado>'
$env:API_TOKEN = '<token-de-teste>'
$env:API_TEST_DATA_SYNTHETIC = 'true'
$env:QA_DEMAND_ID = 'DEMANDA-001'
$env:QA_ANALYST_NAME = '<nome-do-analista>'
mvn test
```

`API_TOKEN` e URLs não são armazenados no repositório. `qa` e `staging` não têm URLs predefinidas porque não constam no Swagger. A URL inicial disponível é de produção; executar contra ela exige `API_ALLOW_PRODUCTION=true` e autorização expressa.

Os cenários cobrem leituras de usuários, criação via `POST /users` e autenticação via `POST /auth/login`. O fluxo de login cria um usuário sintético novo uma vez por execução, verifica o JWT sem expô-lo e reaproveita um cenário outline para as negativas. Não são executados testes de carga ou força bruta. A limpeza de usuários de teste via `DELETE /users/{id}` continua separada e requer token e ID confiável.
Cada cenário deve representar uma chamada HTTP de negócio; o hook de captura registra o último intercâmbio da execução do cenário.

## Relatórios

Ao final de uma execução do runner, o hook captura cenário, requisição, resposta, status, headers, duração e falhas do Karate. O módulo gera HTML, PDF e JSON em uma pasta nova:

```text
reports/<QA_DEMAND_ID>/<AAAA-MM-DD>/<EXEC-ID>/
```

Uma nova execução sempre recebe outro ID e não sobrescreve as anteriores. O nome do analista (`QA_ANALYST_NAME`) e o ID da demanda (`QA_DEMAND_ID`) são necessários; sem essas variáveis, uma execução interativa solicita os valores antes de gerar o documento. Em CI sem console interativo, configure ambas as variáveis.

As evidências são geradas em português do Brasil. Dados pessoais sintéticos só são exibidos com `API_TEST_DATA_SYNTHETIC=true` e `QA_REVEAL_SYNTHETIC_DATA=true`; por padrão, permanecem mascarados. A senha sintética de uma requisição só é exibida também com `QA_REVEAL_SYNTHETIC_PASSWORDS=true`; tokens, cookies e chaves permanecem sempre mascarados, e senhas continuam protegidas nos logs HTTP. `QA_SENSITIVE_FIELDS` permite mascarar campos adicionais.

O HTML nativo do Karate é desativado pelo runner para evitar uma cópia não controlada de respostas ou mensagens de falha; o HTML e o PDF customizados são gerados a partir dos dados capturados e tratados.

## Enviar uma nova demanda

Descreva a funcionalidade e os critérios de aceite, regras de negócio, ambiente autorizado, dados sintéticos disponíveis e credenciais necessárias (nunca cole tokens no chat ou nos arquivos). O agente consultará o inventário e a especificação, apontará ambiguidades, apresentará os cenários e implementará apenas testes compatíveis com o contrato. Cenários sem dependências não atendidas serão marcados como não executados/bloqueados, não como aprovados.
