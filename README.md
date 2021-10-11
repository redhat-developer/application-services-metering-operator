# Application Services Metering operator

The purpose of this operator is to provide the necessary metrics to calculate utilization of Application Services products.

Any product with the following labels will be measured:

- `rht.prod_name`
- `rht.prod_ver`
- `rht.comp`
- `rht.comp_ver`
- `rht.subcomp`
- `rht.subcomp_t`

The operator will produce a metric named `appsvcs_cpu_usage_cores` that has a label of `prod_name`.
The possible values for `prod_name` are:

- Red_Hat_Integration
- Red_Hat_Process_Automation
- Red_Hat_Runtimes

NOTE: A future version of the operator will not aggregate each product instance into Runtimes,
Integration, or Process Automation,
but provide a metric/label combination based on the full set of labels on the pod.

## Related documents

- [Operator release process](RELEASE.md)
- [Operator verification](VERIFICATION.md)
- [Operator bundle process](BUNDLE.md)
