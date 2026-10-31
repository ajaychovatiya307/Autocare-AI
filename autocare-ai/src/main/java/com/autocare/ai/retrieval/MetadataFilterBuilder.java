package com.autocare.ai.retrieval;

import com.autocare.ai.config.AutoCareProperties;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Builds the {@code status}/{@code version}/{@code audience}/{@code vehicle_family} retrieval filter
 * from the authenticated role, per architecture.md §5: only {@code status=ACTIVE} chunks of the
 * currently effective {@code autocare.kb.default-version} are eligible, scoped to the caller's role
 * and resolved vehicle family (falling back via {@link VehicleFamilyResolver} when missing/unsupported).
 */
@Component
public class MetadataFilterBuilder {

    private final VehicleFamilyResolver vehicleFamilyResolver;
    private final AutoCareProperties properties;

    public MetadataFilterBuilder(VehicleFamilyResolver vehicleFamilyResolver, AutoCareProperties properties) {
        this.vehicleFamilyResolver = vehicleFamilyResolver;
        this.properties = properties;
    }

    /** Resolved retrieval filter handed to {@code VectorStoreDocumentRetrieverConfig} (T8.6). */
    public record Filter(DocStatus status, String version, UserRole role, String vehicleFamily) {
    }

    public Filter build(UserRole role, String rawVehicleFamily) {
        return build(role, rawVehicleFamily, DocStatus.ACTIVE, null);
    }

    public Filter build(UserRole role, String rawVehicleFamily, DocStatus status, String version) {
        Assert.notNull(role, "role must not be null");
        Assert.notNull(status, "status must not be null");

        VehicleFamilyResolver.Resolution resolution = vehicleFamilyResolver.resolve(rawVehicleFamily);
        return new Filter(status, version, role, resolution.vehicleFamily());
    }

    public boolean needsVehicleFamilyClarification(String rawVehicleFamily) {
        return vehicleFamilyResolver.resolve(rawVehicleFamily).needsClarification();
    }
}
