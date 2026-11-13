# Estratégia de testes

## Cenários de leitura

Começar por leituras não destrutivas com autenticação explícita. A listagem pode conter dados pessoais: só executá-la em ambiente autorizado com dados sintéticos. O teste por ID é um negativo de contrato que requer um ID confirmado como inexistente.

| ID | Funcionalidade | Endpoint | Método | Cenário | Pré-condições | Resultado esperado | Prioridade | Categoria |
|---|---|---|---|---|---|---|---|---|
| USR-GET-001 | Usuários | `/users` | GET | Retornar usuários cadastrados | Token Bearer; ambiente autorizado; dados sintéticos confirmados | HTTP 200 e corpo presente; schema não publicado | Alta | Smoke / contrato |
| USR-GET-002 | Usuários | `/users/{id}` | GET | Consultar ID confirmado como inexistente | Token Bearer; `API_MISSING_USER_ID` definido e inexistente | HTTP 404 | Média | Negativo / contrato |
| USR-POST-001 | Usuários | `/users` | POST | Omitir `name` | Ambiente autorizado; e-mail e senha sintéticos | HTTP 400 | Alta | Negativo / contrato |
| USR-POST-002 | Usuários | `/users` | POST | Omitir `email` | Ambiente autorizado; nome e senha sintéticos | HTTP 400 | Alta | Negativo / contrato |
| USR-POST-003 | Usuários | `/users` | POST | Omitir `password` | Ambiente autorizado; nome e e-mail sintéticos | HTTP 400 | Alta | Negativo / contrato |
| USR-POST-004 | Usuários | `/users` | POST | Enviar objeto JSON vazio | Ambiente autorizado | HTTP 400 | Alta | Negativo / contrato |
| USR-POST-005 | Usuários | `/users` | POST | Senha com menos de 6 caracteres | Ambiente autorizado; e-mail sintético | HTTP 400 | Alta | Negativo / contrato |
| USR-POST-006 | Usuários | `/users` | POST | E-mail malformado | Ambiente autorizado; dado sintético | HTTP 400 esperado; regra de formato não consta no Swagger | Média | Exploratório |
| USR-POST-007 | Usuários | `/users` | POST | E-mail vazio | Ambiente autorizado; dados sintéticos | HTTP 400 esperado; regra de não-vazio não consta no Swagger | Média | Exploratório |
| USR-POST-008 | Usuários | `/users` | POST | Cadastrar usuário com dados válidos | Ambiente autorizado; e-mail único sintético | HTTP 201; resposta sem schema documentado | Alta | Smoke / contrato |
| USR-POST-009 | Usuários | `/users` | POST | Repetir e-mail recém-cadastrado | USR-POST-008 executado com sucesso | HTTP 409 | Alta | Negativo / contrato |

## Cenários de autenticação

| ID | Endpoint | Cenário | Resultado esperado | Prioridade | Categoria |
|---|---|---|---|---|---|
| AUTH-SETUP-001 | `/users` | Criar uma conta sintética nova por execução | HTTP 201 | Alta | Preparação |
| AUTH-LOGIN-001 | `/auth/login` | Autenticar a conta recém-criada e receber um JWT | HTTP 200 e JWT presente; valor mascarado | Alta | Smoke / contrato |
| AUTH-LOGIN-002 | `/auth/login` | Omitir e-mail | HTTP 400 observado; não documentado | Alta | Exploratório |
| AUTH-LOGIN-003 | `/auth/login` | Omitir senha | HTTP 400 observado; não documentado | Alta | Exploratório |
| AUTH-LOGIN-004 | `/auth/login` | Enviar corpo vazio | HTTP 400 observado; não documentado | Alta | Exploratório |
| AUTH-LOGIN-005 | `/auth/login` | Usar e-mail malformado | HTTP 400 observado; não documentado | Média | Exploratório |
| AUTH-LOGIN-006 | `/auth/login` | Enviar e-mail vazio | HTTP 400 observado; não documentado | Média | Exploratório |
| AUTH-LOGIN-007 | `/auth/login` | Enviar senha vazia | HTTP 400 observado; não documentado | Média | Exploratório |
| AUTH-LOGIN-008 | `/auth/login` | Usar e-mail válido não cadastrado | HTTP 401 | Alta | Negativo / contrato |
| AUTH-LOGIN-009 | `/auth/login` | Usar senha incorreta para a conta criada | HTTP 401 | Alta | Negativo / contrato |

As oito tentativas negativas reutilizam um único `Scenario Outline` e tags de Examples com IDs individuais. O helper `callonce` cria a conta uma única vez e registra o intercâmbio de preparação. As negativas são funcionais, de baixa frequência; não há teste de carga, força bruta ou bloqueio de conta.

## Expansão após confirmação contratual

- Criar testes dos códigos de ausência/invalidez de Bearer nos endpoints protegidos quando esses códigos forem documentados.
- Validar estruturas de sucesso quando os schemas de resposta forem publicados.
- O `POST /users` não declara autenticação no Swagger. Os cenários de criação usam dados sintéticos, e o teste de duplicidade depende do cadastro positivo da mesma execução.
- A limpeza de usuários de teste usa `DELETE /users/{id}`, que requer Bearer JWT e ID retornado pela API. Sem token ou ID confiável, não executar a exclusão nem presumir que o cadastro foi removido.
- O caso de e-mail malformado é exploratório: o Swagger não define formato de e-mail. A resposta observada não deve ser classificada automaticamente como defeito contratual.
- Não executar DELETE nem testes de carga sem aprovação expressa; mesmo com aprovação, interromper a limpeza se não houver token ou ID confiável.
