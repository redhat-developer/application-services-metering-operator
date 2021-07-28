package com.redhat;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1")
@Group("metering.redhat.com")
@Kind("MiddlewareMetering")
@Plural("middlewaremeterings")
public class MiddlewareMetering extends CustomResource<MiddlewareMeteringSpec, MiddlewareMeteringStatus> {}

