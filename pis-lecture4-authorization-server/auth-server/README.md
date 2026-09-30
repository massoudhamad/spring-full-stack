# auth-server — the PIS authorization server

Spring Authorization Server. Issues OAuth2 access tokens (RS256 JWTs), refresh
tokens and OpenID Connect ID tokens for PIS.

```bash
cp .env.example .env         # then set your own passwords and client secrets
mvn spring-boot:run          # port 9100
mvn test
```

| Endpoint | |
| --- | --- |
| `/.well-known/openid-configuration` | Discovery: where everything else is |
| `/oauth2/authorize` | Browser login; returns an authorization code |
| `/oauth2/token` | Code, refresh token or client credentials → tokens |
| `/oauth2/jwks` | Public keys for checking token signatures |
| `/userinfo` | OpenID Connect: who the access token belongs to |
| `/login` | The login page |

| Client | Kind | Grants |
| --- | --- | --- |
| `pis-web` | Public (browser app) | Authorization code + PKCE |
| `pis-bff` | Confidential (server) | Authorization code + PKCE, refresh token |
| `pis-reporting` | Confidential (nightly job) | Client credentials, scope `suppliers.read` |

The lecture is in [`../LECTURE.md`](../LECTURE.md).
