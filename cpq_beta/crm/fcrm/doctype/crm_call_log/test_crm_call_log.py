# Copyright (c) 2023, Frappe Technologies Pvt. Ltd. and Contributors
# See license.txt

import frappe
import json
from frappe.tests.utils import FrappeTestCase
from crm.fcrm.doctype.crm_call_log.crm_call_log import create_lead_from_call_log

class TestCRMCallLog(FrappeTestCase):

    def setUp(self):
        # Ensure we have a note and a call log we can test with.
        # FrappeTestCase usually runs in a transaction that is rolled back.
        pass

    def test_create_lead_from_call_log_str(self):
        # Ensure user has permission
        frappe.set_user("Administrator")

        # Create a dummy note to test the note updating functionality
        note_doc = frappe.get_doc({
            "doctype": "FCRM Note",
            "content": "Test Note for Call Log",
            "title": "Test Call Note"
        }).insert(ignore_permissions=True)

        call_log_doc = frappe.get_doc({
            "doctype": "CRM Call Log",
            "from": "1234567890",
            "to": "0987654321",
            "type": "Incoming",
            "status": "Completed"
        }).insert(ignore_permissions=True)

        call_log = {
            "name": call_log_doc.name,
            "from": "1234567890",
            "note": note_doc.name
        }

        lead_name = create_lead_from_call_log(json.dumps(call_log))

        self.assertTrue(lead_name)

        # Fetch the created lead
        lead = frappe.get_doc("CRM Lead", lead_name)
        self.assertEqual(lead.first_name, "Lead from call 1234567890")
        self.assertEqual(lead.mobile_no, "1234567890")
        self.assertEqual(lead.lead_owner, frappe.session.user)

        # Verify db.set_value calls on Call Log and Note
        updated_call_log = frappe.get_doc("CRM Call Log", call_log_doc.name)
        self.assertEqual(updated_call_log.reference_doctype, "CRM Lead")
        self.assertEqual(updated_call_log.reference_docname, lead.name)

        updated_note = frappe.get_doc("FCRM Note", note_doc.name)
        self.assertEqual(updated_note.reference_doctype, "CRM Lead")
        self.assertEqual(updated_note.reference_docname, lead.name)

    def test_create_lead_from_call_log_dict(self):
        frappe.set_user("Administrator")

        call_log_doc = frappe.get_doc({
            "doctype": "CRM Call Log",
            "from": "9998887776",
            "to": "1112223334",
            "type": "Outgoing",
            "status": "Completed"
        }).insert(ignore_permissions=True)

        call_log = {
            "name": call_log_doc.name,
            "from": "9998887776"
        }

        lead_name = create_lead_from_call_log(call_log)

        self.assertTrue(lead_name)
        lead = frappe.get_doc("CRM Lead", lead_name)
        self.assertEqual(lead.first_name, "Lead from call 9998887776")
        self.assertEqual(lead.mobile_no, "9998887776")

        updated_call_log = frappe.get_doc("CRM Call Log", call_log_doc.name)
        self.assertEqual(updated_call_log.reference_doctype, "CRM Lead")
        self.assertEqual(updated_call_log.reference_docname, lead.name)

    def test_permission_error(self):
        # Create a user without CRM Lead create permission
        if not frappe.db.exists("User", "test_no_perm@example.com"):
            user = frappe.get_doc({
                "doctype": "User",
                "email": "test_no_perm@example.com",
                "first_name": "No Perm",
                "send_welcome_email": 0
            }).insert(ignore_permissions=True)
            # Remove all roles
            user.roles = []
            user.save()

        frappe.set_user("test_no_perm@example.com")

        with self.assertRaises(frappe.PermissionError):
            create_lead_from_call_log({"from": "123"})

        # Reset user
        frappe.set_user("Administrator")

    def test_invalid_type(self):
        frappe.set_user("Administrator")
        with self.assertRaises(frappe.ValidationError):
            create_lead_from_call_log(["invalid list"])

    def test_missing_from(self):
        frappe.set_user("Administrator")

        # Test missing 'from' in dictionary
        with self.assertRaises(frappe.ValidationError):
            create_lead_from_call_log({"name": "test"})

        # Test 'from' is empty string in dictionary
        with self.assertRaises(frappe.ValidationError):
            create_lead_from_call_log({"name": "test", "from": ""})

        # Test 'from' is None in dictionary
        with self.assertRaises(frappe.ValidationError):
            create_lead_from_call_log({"name": "test", "from": None})

        # Test missing 'from' in JSON string
        with self.assertRaises(frappe.ValidationError):
            create_lead_from_call_log('{"name": "test"}')
