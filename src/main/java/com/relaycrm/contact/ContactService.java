package com.relaycrm.contact;

import com.relaycrm.audit.AuditLog;
import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.UserRepository;
import com.relaycrm.common.ApiErrors;
import com.relaycrm.common.OwnedRecordService;
import com.relaycrm.common.RequestReader;
import com.relaycrm.opportunity.OpportunityRepository;

import org.springframework.stereotype.Service;

@Service
public class ContactService extends OwnedRecordService<Contact, ContactRequest> {

    private final OpportunityRepository opportunities;

    public ContactService(ContactRepository contacts, UserRepository users, RequestReader requests,
                          AuditLog audit, OpportunityRepository opportunities) {
        super(contacts, users, requests, audit, ContactRequest.class, "Contact");
        this.opportunities = opportunities;
    }

    @Override
    protected Contact newEntity() {
        return new Contact();
    }

    @Override
    protected void apply(Contact contact, ContactRequest request, CurrentUser user) {
        contact.setFirstName(request.firstName());
        contact.setLastName(request.lastName());
        contact.setEmail(request.email());
        contact.setPhone(request.phone());
        contact.setCompany(request.company());
    }

    /**
     * A contact that deals still link to cannot be deleted: the database would
     * refuse it (foreign key), so say so clearly instead, with the number of deals.
     * The contact's row is locked at this point, so no new deal can link to it
     * between this check and the delete.
     */
    @Override
    protected void beforeDelete(CurrentUser user, Contact contact) {
        long linked = opportunities.countByOrgIdAndContactId(user.orgId(), contact.getId());
        if (linked > 0) {
            throw ApiErrors.conflict("Contact " + contact.getId() + " is still linked to " + linked
                    + (linked == 1 ? " opportunity" : " opportunities")
                    + ". Remove the link or delete " + (linked == 1 ? "it" : "them") + " first");
        }
    }

    @Override
    protected ContactRequest toRequest(Contact contact) {
        return new ContactRequest(contact.getFirstName(), contact.getLastName(), contact.getEmail(),
                contact.getPhone(), contact.getCompany(), contact.getOwnerId());
    }
}
