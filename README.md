Shadowmute SMTP
===============

An inbound-only implementation of SMTP for the Shadowmute Identity-as-a-Service platform.

Building Locally
----------------

To build and test, use the following sbt commands:

```
sbt clean compile coverage test coverageReport
```

Local Development
-----------------

The included `docker-compose` sets up intended ancillary services. 

### Generating TLS Certificates

For STARTTLS support, Shadowmute requires a certificate. StartTLS does not usually perform any form of certificate 
validation, so a self-signed certificate is usually adequate. 

To generate a certificate suitable for local development, you can use the following openssl commands:

```
openssl genrsa -out development_key.pem 4096

openssl req -new -key development_key.pem -out shadowmute_csr.pem \
  -subj "/C=US/ST=Oregon/L=Portland/O=Shadowmute/OU=Mail Ingest/CN=shadowmute.example.com"

openssl x509 -in shadowmute_csr.pem -out shadowmute_smtp_cert.pem \
  -req -signkey development_key.pem -days 365

openssl pkcs12 -export -in shadowmute_smtp_cert.pem \
  -inkey development_key.pem -out local_dev.p12 -passout pass:testing_locally
``` 

To validate the certificate: (note the passphrase was `testing_locally` above)

```
openssl pkcs12 -info -in ./local_dev.p12
```

## Testing a STARTTLS connection

By default, Shadowmute SMTP will listen on TCP port 2025. To validate explicit TLS (STARTTLS), you can use openssl.

```
openssl s_client -debug -connect localhost:2025 -starttls smtp -crlf
```

Deployment
----------

It is recommended to run Shadowmute SMTP behind a reverse proxy that will support remapping to the expected plaintext 
TCP port (25) as well as support for implicit TLS on TCP port 465. 

Building with Gitlab-Runner
---------------------------

The project was originally designed to be built using the Gitlab CI platform. To run a gitlab build locally, use the 
following command:

```
gitlab-runner exec docker build:smtp
```

