// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.RotationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;

import java.net.URI;
import java.util.Objects;

/**
 * Represents an application's endpoint. The endpoint scope can either be global or a specific zone. This is visible to
 * the tenant and is used by the tenant when accessing deployments.
 *
 * @author mpolden
 */
public class Endpoint {

    public static final String YAHOO_DNS_SUFFIX = ".vespa.yahooapis.com";
    public static final String OATH_DNS_SUFFIX = ".vespa.oath.cloud";
    public static final String PUBLIC_DNS_SUFFIX = ".public.vespa.oath.cloud";

    private final URI url;
    private final Scope scope;
    private final boolean legacy;
    private final boolean directRouting;
    private final boolean tls;

    private Endpoint(String name, ApplicationId application, ZoneId zone, SystemName system, Port port, boolean legacy,
                     boolean directRouting) {
        Objects.requireNonNull(name, "name must be non-null");
        Objects.requireNonNull(application, "application must be non-null");
        Objects.requireNonNull(system, "system must be non-null");
        Objects.requireNonNull(port, "port must be non-null");
        this.url = createUrl(name, application, zone, system, port, legacy, directRouting);
        this.scope = zone == null ? Scope.global : Scope.zone;
        this.legacy = legacy;
        this.directRouting = directRouting;
        this.tls = port.tls;
    }

    /** Returns the URL used to access this */
    public URI url() {
        return url;
    }

    /** Returns the DNS name of this */
    public String dnsName() {
        String host = url.getHost();
        if (host != null)
            return host;
        return url.getAuthority();
    }

    /** Returns the scope of this */
    public Scope scope() {
        return scope;
    }

    /** Returns whether this is considered a legacy DNS name that is due for removal */
    public boolean legacy() {
        return legacy;
    }

    /**
     * Returns whether this endpoint supports direct routing. Direct routing means that this endpoint is served by an
     * exclusive load balancer instead of a shared routing layer.
     */
    public boolean directRouting() {
        return directRouting;
    }

    /** Returns whether this endpoint supports TLS connections */
    public boolean tls() {
        return tls;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Endpoint endpoint = (Endpoint) o;
        return url.equals(endpoint.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return String.format("endpoint %s [scope=%s, legacy=%s, directRouting=%s]", url, scope, legacy, directRouting);
    }

    private static URI createUrl(String name, ApplicationId application, ZoneId zone, SystemName system,
                                 Port port, boolean legacy, boolean directRouting) {
        String scheme = port.tls ? "https" : "http";
        String separator = separator(system, directRouting, port.tls);
        String portPart = port.isDefault() ? "" : ":" + port.port;
        return URI.create(scheme + "://" +
                          sanitize(namePart(name, separator)) +
                          systemPart(system, separator) +
                          sanitize(instancePart(application, zone, separator)) +
                          sanitize(application.application().value()) +
                          separator +
                          sanitize(application.tenant().value()) +
                          "." +
                          scopePart(zone, legacy) +
                          dnsSuffix(system, legacy) +
                          portPart +
                          "/");
    }

    private static String sanitize(String part) { // TODO: Reject reserved words
        return part.replace('_', '-');
    }

    private static String separator(SystemName system, boolean directRouting, boolean tls) {
        if (!tls) return ".";
        if (directRouting) return ".";
        if (isPublic(system)) return ".";
        return "--";
    }

    private static String namePart(String name, String separator) {
        if ("default".equals(name)) return "";
        return name + separator;
    }

    private static String scopePart(ZoneId zone, boolean legacy) {
        if (zone == null) return "global";
        if (!legacy && zone.environment().isProduction()) return zone.region().value(); // Skip prod environment for non-legacy endpoints
        return zone.region().value() + "." + zone.environment().value();
    }

    private static String instancePart(ApplicationId application, ZoneId zone, String separator) {
        if (zone == null) return ""; // Always omit instance for global endpoints
        if (application.instance().isDefault()) return ""; // Skip "default"
        return application.instance().value() + separator;
    }

    private static String systemPart(SystemName system, String separator) {
        if (system == SystemName.main || isPublic(system)) return "";
        return system.name() + separator;
    }

    private static String dnsSuffix(SystemName system, boolean legacy) {
        switch (system) {
            case cd:
            case main:
                if (legacy) return YAHOO_DNS_SUFFIX;
                return OATH_DNS_SUFFIX;
            case Public:
            case vaas:
                return PUBLIC_DNS_SUFFIX;
            default: throw new IllegalArgumentException("No DNS suffix declared for system " + system);
        }
    }

    private static boolean isPublic(SystemName system) { // TODO: Remove and inline once we're down to one
        return system == SystemName.Public || system == SystemName.vaas;
    }

    /** An endpoint's scope */
    public enum Scope {

        /** Endpoint points to all zones */
        global,

        /** Endpoint points to a single zone */
        zone,

    }

    /** Represents an endpoint's HTTP port */
    public static class Port {

        private final int port;
        private final boolean tls;

        private Port(int port, boolean tls) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535, got " + port);
            }
            this.port = port;
            this.tls = tls;
        }

        private boolean isDefault() {
            return port == 80 || port == 443;
        }

        /** Returns the default HTTPS port */
        public static Port tls() {
            return new Port(443, true);
        }

        /** Create a HTTPS port */
        public static Port tls(int port) {
            return new Port(port, true);
        }

        /** Create a HTTP port */
        public static Port plain(int port) {
            return new Port(port, false);
        }

    }

    /** Build an endpoint for given application */
    public static EndpointBuilder of(ApplicationId application) {
        return new EndpointBuilder(application);
    }

    public static class EndpointBuilder {

        private final ApplicationId application;

        private ZoneId zone;
        private ClusterSpec.Id cluster;
        private RotationName rotation;
        private Port port;
        private boolean legacy = false;
        private boolean directRouting = false;

        private EndpointBuilder(ApplicationId application) {
            this.application = application;
        }

        /** Sets the cluster and zone target of this  */
        public EndpointBuilder target(ClusterSpec.Id cluster, ZoneId zone) {
            if (rotation != null) {
                throw new IllegalArgumentException("Cannot set both cluster and rotation target");
            }
            this.cluster = cluster;
            this.zone = zone;
            return this;
        }

        /** Sets the rotation target of this */
        public EndpointBuilder target(RotationName rotation) {
            if (cluster != null && zone != null) {
                throw new IllegalArgumentException("Cannot set both cluster and rotation target");
            }
            this.rotation = rotation;
            return this;
        }

        public EndpointBuilder wildcardRotationTarget() {
            if(cluster != null || zone != null) {
                throw new IllegalArgumentException("Cannot set both cluster and rotation target");
            }
            this.rotation = RotationName.from("*");
            return this;
        }

        public EndpointBuilder wildcardZoneTarget(ZoneId zone) {
            if(rotation != null) {
                throw new IllegalArgumentException("Cannot set both cluster and rotation target");
            }
            this.zone = zone;
            this.cluster = ClusterSpec.Id.from("*");
            return this;
        }

        /** Sets the port of this  */
        public EndpointBuilder on(Port port) {
            this.port = port;
            return this;
        }

        /** Marks this as a legacy endpoint */
        public EndpointBuilder legacy() {
            this.legacy = true;
            return this;
        }

        /** Enables direct routing support for this */
        public EndpointBuilder directRouting() {
            this.directRouting = true;
            return this;
        }

        /** Sets the system that owns this */
        public Endpoint in(SystemName system) {
            String name;
            if (cluster != null && zone != null) {
                name = cluster.value();
            } else if (rotation != null) {
                name = rotation.value();
            } else {
                throw new IllegalArgumentException("Must set either cluster or rotation target");
            }
            if (isPublic(system) && !directRouting) {
                throw new IllegalArgumentException("Public system only supports direct routing endpoints");
            }
            if (directRouting && !port.isDefault()) {
                throw new IllegalArgumentException("Direct routing endpoints only support default port");
            }
            return new Endpoint(name, application, zone, system, port, legacy, directRouting);
        }

    }

}
