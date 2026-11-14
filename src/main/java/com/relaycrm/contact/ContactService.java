package com.relaycrm.contact;

import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.UserRepository;
import com.relaycrm.common.OwnedRecordService;
import com.relaycrm.common.RequestReader;

import org.springframework.stereotype.Service;

@Service
public class ContactService extends OwnedRecordService<Contact, ContactRequest> {

    public ContactService(ContactRepository contacts, UserRepository users, RequestReader requests) {
        super(contacts, users, requests, ContactRequest.class, "Contact");
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

    @Override
    protected ContactRequest toRequest(Contact contact) {
        return new ContactRequest(contact.getFirstName(), contact.getLastName(), contact.getEmail(),
                contact.getPhone(), contact.getCompany(), contact.getOwnerId());
    }
}
