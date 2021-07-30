package com.redhat;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1")
@Group("applicationservices.redhat.com")
@Kind("Meter")
@Plural("meters")
public class Meter extends CustomResource<MeterSpec, MeterStatus> {}

