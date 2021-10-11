# Operator verification process

There are two different verifications available, depending on what is being verified:

1. The [cluster deployment](#cluster-deployment) approach utilizes a built image, but without the OLM configuration of the [OperatorHub](#operatorhub) approach.
2. The [OperatorHub](#operatorhub) way enables the validation of OperatorHub bundle configuration with an OpenShift environment. This includes all the necessary config for integration with OLM (Operator Lifecycle Manager).

## Cluster deployment

This will deploy an image of the operator into a Kubernetes/OpenShift cluster.

```shell script
docker login quay.io
```

Make and push a Docker image of the operator:

```shell script
make docker-build docker-push IMG=quay.io/{user}/application-services-operator:{version}
```

Utilize the operator make commands to install the CRD and deploy the operator:

```shell script
make install
make deploy
cat <<EOF | kubectl apply -f -
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: application-services-metering-operator-admin
subjects:
- kind: ServiceAccount
  name: application-services-metering-operator
  namespace: default
roleRef:
  kind: ClusterRole
  name: cluster-admin
  apiGroup: ""
EOF
```

Verify the operator is running:

```shell script
kubectl get all
```

Install a Meter custom resource to commence metering:

```shell script
cat <<EOF | kubectl apply -f -
apiVersion: applicationservices.redhat.com/v1
kind: Meter
metadata:
  name: application-services-metering-operator
spec:
  includeInfrastructure: true
  meterCollectionEnabled: true
EOF
```

## OperatorHub

There are two repositories that can be used for verifying a new operator release:

- [OperatorHub.io](https://github.com/k8s-operatorhub/community-operators/tree/main/operators)
- [OperatorHub packaged in OpenShift and OKD](https://github.com/redhat-openshift-ecosystem/community-operators-prod)

One of these git repositories will be required to validate the operator through the OperatorHub.

### Requirements

- Podman running
    - For macOS, `podman machine init` and then `podman machine start`
- [OPM](https://github.com/operator-framework/operator-registry/releases/latest) installed and available on path

### Validate Operator Bundle

From within a version directory under /operators/application-services-metering-operator, run:

```shell-script
operator-sdk bundle validate --select-optional name=operatorhub .
```

This command provides details on any errors in the operator manifests that need resolution.
At the current time, the following messages are expected and don't cause an issue:

```shell-script
ERRO[0000] Error: Value application-services-metering-operator: invalid service account found in bundle. sa name cannot match service account defined for deployment spec in CSV 
ERRO[0000] Error: Value application-services-metering-operator: invalid service account found in bundle. sa name cannot match service account defined for deployment spec in CSV 
WARN[0000] Warning: Value : (application-services-metering-operator.v0.6.0) csv.Spec.minKubeVersion is not informed. It is recommended you provide this information. Otherwise, it would mean that your operator project can be distributed and installed in any cluster version available, which is not necessarily the case for all projects.
```

### Publish operator metadata to a catalog

From the /operators/application-services-metering-operator directory:

```shell-script
podman build -f {version}/Dockerfile -t application-services-metering-operator:v{version} {version}/
podman push application-services-metering-operator:v{version} quay.io/{user}/application-services-metering-operator:v{version}
```

Where `{version}` should be replaced with the version matching the bundle directory name for the version being tested.

Login in to quay.io:

```shell-script
podman login quay.io
```

Package an OperatorHub catalog that includes the version of the operator to be verified:

```shell-script
opm index add --bundles quay.io/{user}/application-services-metering-operator:v{version} --from-index quay.io/operatorhubio/catalog:latest --tag quay.io/{user}/my-test-catalog:latest
```

NOTE: Need to run `podman login quay.io` before each use of `opm index`!

```shell-script
podman push quay.io/{user}/my-test-catalog:latest
```

### Create CatalogSource for Marketplace

We need to update the OperatorHub catalog in the OpenShift instance used for testing.
Once logged in with `oc login`, run:

```shell-script
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: my-test-catalog
  namespace: openshift-marketplace
spec:
  sourceType: grpc
  image: quay.io/{user}/my-test-catalog:latest
EOF
```

Allow about 10s after success before accessing the OperatorHub to ensure it's been reloaded.

### Install operator from OperatorHub

Open OperatorHub and search for "meter" to find the operator.
Select it, verify the version available is the one added to the temporary catalog,
and click "Install".

Once installed, open the operator view and click "Meter" and "Create Meter".

Be sure to select "includeInfrastructure" if you want to measure infrastructure components as well as application ones.

## View metrics from operator

Click on "Monitoring" and then "Metrics" in the left hand menu of the OpenShift Console.
In the search field, start typing `appsvcs_cpu_usage_cores` or `appsvcs_cores_by_product:sum`,
select the desired metric, and then run the query.

If workloads are running that utilize the required labels, they will be aggregated under one of the three Application Services product areas: Runtimes, Integration, and Process Automation.
