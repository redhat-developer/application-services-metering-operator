# OperatorHub.io Bundles

This document contains information on how to update and release new versions of the operator
that will be made available in OperatorHub through OLM.

## Process to release a bundle

1. Fork and clone the [OperatorHub.io](https://github.com/k8s-operatorhub/community-operators) repository
2. Navigate to _/operators/application-services-metering-operator_
3. Copy the previous version directory and paste it to the same place with a name for the new version
4. Update the version across all files in the directory
5. After `spec`.`version`, update the `replaces` key (or add if first update) with the version of the operator that will be replaceable with this update.
6. Update the operator deployment in the CSV, or other manifest files, as needed based on any changes in the released Kubernetes.yml files of the operator release.
7. Following the [verification instructions](VERIFICATION.md#operatorhub) to ensure nothing is obviously broken.
8. If the descriptions were updated, ensure they look ok with the [preview tool](https://operatorhub.io/preview).
9. Open a PR against the repo, following the PR checklist.

Additional documentation on this process can be found [here](https://k8s-operatorhub.github.io/community-operators/).

Once this PR is merged, do the same for the [OperatorHub packaged in OpenShift and OKD](https://github.com/redhat-openshift-ecosystem/community-operators-prod) repository.
