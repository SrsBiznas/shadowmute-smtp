Shadowmute SMTP
===============

An inbound-only implementation of SMTP for the Shadowmute Identity-as-a-Service platform.

Building with Gitlab-Runner
---------------------------

The project was designed to be built using the Gitlab CI platform. To run a gitlab build
locally, use the following command:

```
gitlab-runner exec docker build:smtp
```