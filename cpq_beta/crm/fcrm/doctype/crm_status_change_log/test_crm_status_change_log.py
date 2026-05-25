# Copyright (c) 2024, Frappe Technologies Pvt. Ltd. and Contributors
# See license.txt

import frappe
from frappe.tests.utils import FrappeTestCase
from unittest.mock import MagicMock
from datetime import datetime, timedelta
from crm.fcrm.doctype.crm_status_change_log.crm_status_change_log import get_duration, add_status_change_log

class TestCRMStatusChangeLog(FrappeTestCase):
    def setUp(self):
        # Set a standard user for frappe.session.user
        frappe.set_user("Administrator")

    def test_get_duration_with_datetime_objects(self):
        from_date = datetime(2024, 1, 1, 10, 0, 0)
        to_date = datetime(2024, 1, 1, 10, 5, 30)
        duration = get_duration(from_date, to_date)
        self.assertEqual(duration, 330.0)

    def test_get_duration_with_string_dates(self):
        from_date_str = "2024-01-01 10:00:00"
        to_date_str = "2024-01-01 10:10:00"
        duration = get_duration(from_date_str, to_date_str)
        self.assertEqual(duration, 600.0)

    def test_get_duration_mixed_types(self):
        from_date_str = "2024-01-01 10:00:00"
        to_date = datetime(2024, 1, 1, 11, 0, 0)
        duration = get_duration(from_date_str, to_date)
        self.assertEqual(duration, 3600.0)

    def test_add_status_change_log_new_doc(self):
        # When doc is new, it should just append one log entry
        mock_doc = MagicMock()
        mock_doc.is_new.return_value = True
        mock_doc.status = "Open"

        appended_logs = []
        def mock_append(fieldname, value_dict):
            if fieldname == "status_change_log":
                appended_logs.append(value_dict)
        mock_doc.append.side_effect = mock_append

        add_status_change_log(mock_doc)

        self.assertEqual(len(appended_logs), 1)
        self.assertEqual(appended_logs[0]["from"], "Open")
        self.assertEqual(appended_logs[0]["to"], "")
        self.assertIsInstance(appended_logs[0]["from_date"], datetime)
        self.assertEqual(appended_logs[0]["to_date"], "")

    def test_add_status_change_log_existing_doc_no_previous_logs(self):
        mock_doc = MagicMock()
        mock_doc.is_new.return_value = False
        mock_doc.status = "Closed"

        mock_prev_doc = MagicMock()
        mock_prev_doc.status = "Open"
        mock_doc.get_doc_before_save.return_value = mock_prev_doc

        class MockRow:
            def __init__(self, **kwargs):
                for k, v in kwargs.items():
                    setattr(self, k, v)

        logs = []
        mock_doc.status_change_log = logs

        def mock_append(fieldname, value_dict):
            if fieldname == "status_change_log":
                row = MockRow(**value_dict)
                logs.append(row)
                return row
        mock_doc.append.side_effect = mock_append

        add_status_change_log(mock_doc)

        self.assertEqual(len(logs), 2)

        old_log = logs[0]
        self.assertEqual(old_log.to, "Closed")
        self.assertIsNotNone(getattr(old_log, 'from_date', None))
        self.assertIsNotNone(old_log.to_date)
        self.assertTrue(old_log.duration >= 0)
        self.assertTrue(hasattr(old_log, 'log_owner'))

        new_log = logs[1]
        self.assertEqual(getattr(new_log, 'from'), "Closed")
        self.assertEqual(new_log.to, "")
        self.assertIsInstance(getattr(new_log, 'from_date'), datetime)

    def test_add_status_change_log_existing_doc_with_previous_logs(self):
        mock_doc = MagicMock()
        mock_doc.is_new.return_value = False
        mock_doc.status = "Resolved"

        mock_prev_doc = MagicMock()
        mock_prev_doc.status = "In Progress"
        mock_doc.get_doc_before_save.return_value = mock_prev_doc

        class MockRow:
            def __init__(self, **kwargs):
                for k, v in kwargs.items():
                    setattr(self, k, v)

        existing_log = MockRow(**{
            "from": "Open",
            "to": "",
            "from_date": datetime.now() - timedelta(minutes=10),
            "to_date": "",
            "log_owner": "test@example.com"
        })
        logs = [existing_log]

        mock_doc.status_change_log = logs

        def mock_append(fieldname, value_dict):
            if fieldname == "status_change_log":
                row = MockRow(**value_dict)
                logs.append(row)
                return row
        mock_doc.append.side_effect = mock_append

        add_status_change_log(mock_doc)

        self.assertEqual(len(logs), 2)

        updated_log = logs[0]
        self.assertEqual(updated_log.to, "Resolved")
        self.assertIsInstance(updated_log.to_date, datetime)
        self.assertTrue(updated_log.duration >= 0)

        new_log = logs[1]
        self.assertEqual(getattr(new_log, 'from'), "Resolved")
        self.assertEqual(new_log.to, "")
