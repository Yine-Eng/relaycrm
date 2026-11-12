package com.relaycrm.contact;

import com.relaycrm.auth.CurrentUser;
import com.relaycrm.auth.UserRepository;
import com.relaycrm.common.OwnedRecordService;

import org.springframework.stereotype.Service;

@Service
public class ContactService extends OwnedRecordService<Contact, ContactRequest> {

    public ContactService(ContactRepository contacts, UserRepository users) {
        super(contacts, users, "Contact");
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
}
