package org.softtech.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;


/**
 * Identifies the specific enterprise functional module within the SoftTech ERP Suite
 * impacted by reported support ticket
 *
 * Encapsulates domain classification, operational criticality weighting, and automated
 * escalation flag. In compliance with ISO/IEC 25010 Reliability (Fault Tolerance)
 * and CMMI Level 2/3 Service Management standards, module categorization feeds directly
 * into dynamic triage algorithms and root-cause analysis metrics
 *
 */
@Getter
@AllArgsConstructor
public enum ErpModule {

    /**
     * Core financial management, general ledger, treasury, and multi-currency accounting operations.
     * Criticality: Maximum (4). Disruptions directly compromise corporate financial reporting and audits.
     */
    FINANCIAL("FIN", "Financial & Accounting", 4, true),

    /**
     * Real-time electronic invoicing, tax compliance reporting, and fiscal integration pipelines.
     * Criticality: Maximum (4). Failures risk regulatory non-compliance penalties and frozen commerce.
     */
    BILLING("BIL", "Electronic Billing & Invoicing", 4, true),

    /**
     * Real-time warehouse inventory, automated reordering thresholds, and physical stock tracking.
     * Criticality: High (3). Affects distribution pipelines and physical order fulfillment.
     */
    INVENTORY("INV", "Inventory & Warehouse Management", 3, false),

    /**
     * Point of Sale (POS), omnichannel retail checkouts, and customer quote dispatching.
     * Criticality: High (3). Directly impacts front-line customer transactions and store revenues.
     */
    SALES("SAL", "Sales & Point of Sale (POS)", 3, false),

    /**
     * Customer relationship management, lead generation, and omnichannel communication history.
     * Criticality: Medium (2). Non-blocking for immediate core financial and physical logistics.
     */
    CRM("CRM", "Customer Relationship Management", 2, false),

    /**
     * Payroll generation, labor compliance contracts, and employee attendance auditing.
     * Criticality: Medium (2). Time-sensitive during payout cycles, otherwise standard operational queue.
     */
    HUMAN_RESOURCES("HR", "Human Resources & Payroll", 2, false),

    /**
     * Global procurement, vendor evaluation workflows, and purchase order tracking.
     * Criticality: Medium (2). Intercompany workflows with standard operational buffers.
     */
    SUPPLY_CHAIN("SCM", "Supply Chain & Procurement", 2, false),

    /**
     * Centralized platform configuration, user roles (RBAC), and technical environment settings.
     * Criticality: Maximum (4). Configuration defects can cascade across all dependent modules.
     */
    CORE_SYSTEM("COR", "Core System Administration", 4, true);

    private final String code;
    private final String displayName;
    private final int criticalityWeight;
    private final boolean requiresSupervisorEscalation;


    /**
     * Evaluates whether this module is categorized as mission-critical to the core ERP infrastructure.
     *
     * @return True if the criticality weight is equal to or greater than 4; True otherwise.
     */
    public boolean isMissionCritical(){
        return this.criticalityWeight >= 4;
    }

}
