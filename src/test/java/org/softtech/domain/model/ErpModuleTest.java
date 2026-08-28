package org.softtech.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test suite for the {@link ErpModule} enumeration.
 */
class ErpModuleTest {

    @Test
    @DisplayName("Should classify mission-critical modules by criticality weight")
    void shouldClassifyMissionCriticalModules() {
        assertTrue(ErpModule.FINANCIAL.isMissionCritical());
        assertTrue(ErpModule.BILLING.isMissionCritical());
        assertTrue(ErpModule.CORE_SYSTEM.isMissionCritical());

        assertFalse(ErpModule.INVENTORY.isMissionCritical());
        assertFalse(ErpModule.SALES.isMissionCritical());
        assertFalse(ErpModule.CRM.isMissionCritical());
        assertFalse(ErpModule.HUMAN_RESOURCES.isMissionCritical());
        assertFalse(ErpModule.SUPPLY_CHAIN.isMissionCritical());
    }

    @Test
    @DisplayName("Should flag supervisor escalation for critical modules")
    void shouldFlagEscalation() {
        assertTrue(ErpModule.FINANCIAL.isRequiresSupervisorEscalation());
        assertTrue(ErpModule.BILLING.isRequiresSupervisorEscalation());
        assertTrue(ErpModule.CORE_SYSTEM.isRequiresSupervisorEscalation());

        assertFalse(ErpModule.INVENTORY.isRequiresSupervisorEscalation());
        assertFalse(ErpModule.CRM.isRequiresSupervisorEscalation());
        assertFalse(ErpModule.HUMAN_RESOURCES.isRequiresSupervisorEscalation());
    }
}
