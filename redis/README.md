# Redis Service

This project uses the official `redis:7-alpine` image for the database tier.

The `words` API seeds the required noun, verb, and adjective datasets into Redis on startup when the keys do not already exist.
