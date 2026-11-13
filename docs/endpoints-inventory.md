# Inventário de endpoints

Base URL de produção documentada: `https://projetinho-nu-two.vercel.app`.

| Método | Endpoint | Autenticação | Respostas documentadas | Entrada / observações |
|---|---|---|---|---|
| POST | `/users` | Não indicada | 201, 400, 409 | Corpo obrigatório `CreateUserDto`: `name`, `email`, `password`; e-mail único; senha com mínimo de 6 caracteres na descrição. |
| GET | `/users` | Bearer JWT | 200 | Retorna todos os usuários; schema de resposta ausente. Pode expor dados pessoais. |
| GET | `/users/{id}` | Bearer JWT | 404 | `id` numérico obrigatório; schema de resposta de sucesso ausente. |
| PATCH | `/users/{id}` | Bearer JWT | 404, 409 | `id` numérico e corpo obrigatório `UpdateUserDto`; propriedades opcionais. |
| DELETE | `/users/{id}` | Bearer JWT | 204, 404 | `id` numérico; operação destrutiva. Não incluída nos testes iniciais. |
| POST | `/auth/login` | Não indicada | 200, 401 | Corpo obrigatório `LoginDto`: `email`, `password`; resposta de token sem schema. |

## Dependências identificadas

- `GET /users`, `GET /users/{id}`, `PATCH /users/{id}` e `DELETE /users/{id}` requerem Bearer JWT segundo o Swagger.
- `POST /auth/login` não declara autenticação de entrada e documenta HTTP 200 e 401; a resposta de sucesso não tem schema publicado.
- O fluxo de teste de login cria um usuário sintético novo por execução com `POST /users`, autentica essa conta e verifica a presença de um JWT sem expor o token.
- Operações de criação, atualização e exclusão não são necessárias para os testes iniciais de leitura.

## Testes iniciais

1. `GET /users`: esperado `200`; só executar com token e confirmação `API_TEST_DATA_SYNTHETIC=true`, pois o endpoint pode retornar dados pessoais e o relatório Karate pode registrar o corpo.
2. `GET /users/{id}`: esperado `404` usando `API_MISSING_USER_ID` confirmado como inexistente no ambiente autorizado. Não presume um ID fictício.
3. `POST /auth/login`: conta nova por execução; cenário positivo para HTTP 200/JWT e negativas reutilizáveis para dados ausentes, vazios, e-mail malformado, usuário inexistente e senha incorreta. O Swagger não documenta os códigos de validação de entrada; os HTTP 400 observados são exploratórios.

Os cenários sem pré-condições são abortados pelo Karate e não contam como aprovados. A leitura de usuários só deve ser habilitada em ambiente com dados sintéticos controlados.
