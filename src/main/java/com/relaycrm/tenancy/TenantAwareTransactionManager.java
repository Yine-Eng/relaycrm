package com.relaycrm.tenancy;

import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.CurrentUserContext;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells PostgreSQL which organization the current transaction belongs to.
 *
 * Right after a transaction begins, it sets the transaction-local setting
 * app.current_org to the caller's organization. The row-level security
 * policies (V1__schema.sql) compare every row's org_id against it. Because
 * the setting is transaction-local it disappears on commit/rollback, so a
 * pooled connection can never carry one tenant's identity into another
 * request. With no authenticated caller nothing is set, and the policies
 * then hide every row.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    public TenantAwareTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        CurrentUser user = CurrentUserContext.get().orElse(null);
        if (user == null) {
            return;
        }
        try {
            EntityManagerHolder holder = (EntityManagerHolder)
                    TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
            holder.getEntityManager()
                    .createNativeQuery("select set_config('app.current_org', :org, true)")
                    .setParameter("org", String.valueOf(user.orgId()))
                    .getSingleResult();
        } catch (RuntimeException e) {
            doCleanupAfterCompletion(transaction);
            throw e;
        }
    }
}
