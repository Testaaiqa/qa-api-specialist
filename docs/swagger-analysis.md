# Análise do Swagger

- **Documentação consultada:** https://projetinho-nu-two.vercel.app/api-docs
- **Especificação:** OpenAPI 3.0.0, incorporada em `swagger-ui-init.js` (não foi identificada uma URL JSON/YAML separada).
- **API:** `API com NestJS, Prisma e Swagger`, versão `1.0`.
- **Servidor de produção documentado:** `https://projetinho-nu-two.vercel.app`
- **Servidor local documentado:** `http://localhost:3000`
- **Autenticação:** HTTP Bearer, formato JWT, esquema `access-token`.
- **Schemas:** `CreateUserDto`, `UpdateUserDto`, `LoginDto`.

## Limitações da especificação

- Não há schemas documentados para respostas.
- `GET /users/{id}` documenta `404`, mas não descreve a resposta de sucesso.
- Não estão documentados códigos de autenticação/autorização para os endpoints protegidos.
- `CreateUserDto` marca `name`, `email` e `password` como obrigatórios; a descrição de senha informa mínimo de 6 caracteres, sem restrições adicionais de formato.
- A documentação não declara regra para CPF.
- Nenhum endpoint foi inferido além dos descritos na especificação.

Como a especificação não fornece schemas de resposta, os testes iniciais se limitam aos códigos explicitamente documentados e a verificações básicas de presença de corpo onde aplicável. Não há assertivas de formato de erro ou conteúdo não definido pelo contrato.
