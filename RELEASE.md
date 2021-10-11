# Operator release process

1. Go to the "Operator release" GH Action [page](https://github.com/redhat-developer/application-services-metering-operator/actions/workflows/release.yml)
2. Click the "Run workflow" drop down
3. Enter a release/tag version and the next development version with `-SNAPSHOT` suffix
4. Click "Run workflow" button to perform the release

The release process will do the following:

- Tag the code with the desired version
- Update the `main` branch to the new development version
- Push the built Docker image to the operator [repository](https://quay.io/repository/redhat-developer/application-services-metering-operator) on quay.io
