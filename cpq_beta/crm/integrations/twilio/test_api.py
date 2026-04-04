import sys
import unittest
from unittest.mock import MagicMock, patch

# In environments where Frappe is not installed, the frappe module must be mocked in sys.modules
if 'frappe' not in sys.modules:
	frappe_mock = MagicMock()
	# This makes @frappe.whitelist() a no-op decorator that just returns the original function
	def whitelist(*args, **kwargs):
		def decorator(f):
			return f
		return decorator
	frappe_mock.whitelist = whitelist
	sys.modules['frappe'] = frappe_mock
	sys.modules['frappe.utils'] = MagicMock()
	sys.modules['frappe.utils.password'] = MagicMock()
	sys.modules['werkzeug'] = MagicMock()
	sys.modules['werkzeug.wrappers'] = MagicMock()

	twilio_mock = MagicMock()
	sys.modules['twilio'] = twilio_mock
	sys.modules['twilio.rest'] = MagicMock()
	sys.modules['twilio.request_validator'] = MagicMock()
	sys.modules['twilio.twiml'] = MagicMock()
	sys.modules['twilio.twiml.voice_response'] = MagicMock()
	sys.modules['twilio.jwt'] = MagicMock()
	sys.modules['twilio.jwt.access_token'] = MagicMock()
	sys.modules['twilio.jwt.access_token.grants'] = MagicMock()

import frappe
from crm.integrations.twilio.api import generate_access_token

class TestTwilioAPI(unittest.TestCase):
	def setUp(self):
		self.twilio_connect_patcher = patch('crm.integrations.twilio.api.Twilio.connect')
		self.mock_connect = self.twilio_connect_patcher.start()

		# Set frappe specific things using patch to ensure they are restored
		self.frappe_db_get_value_patcher = patch('frappe.db.get_value')
		self.mock_db_get_value = self.frappe_db_get_value_patcher.start()

		# Session patch
		if not hasattr(frappe, 'session'):
			frappe.session = MagicMock()
		self.frappe_session_user_patcher = patch.object(frappe.session, 'user', 'test_user@example.com')
		self.frappe_session_user_patcher.start()

		# Safe decode patch
		self.frappe_safe_decode_patcher = patch('frappe.safe_decode', side_effect=lambda x: x.decode('utf-8') if isinstance(x, bytes) else x)
		self.mock_safe_decode = self.frappe_safe_decode_patcher.start()

	def tearDown(self):
		self.twilio_connect_patcher.stop()
		self.frappe_db_get_value_patcher.stop()
		self.frappe_session_user_patcher.stop()
		self.frappe_safe_decode_patcher.stop()

	def test_generate_access_token_success(self):
		# Setup
		mock_twilio = MagicMock()
		self.mock_connect.return_value = mock_twilio

		# Mocking token bytes returning from Twilio
		mock_twilio.generate_voice_access_token.return_value = b'mock_token'

		self.mock_db_get_value.return_value = '+1234567890'

		# Execute
		result = generate_access_token()

		# Verify
		self.mock_connect.assert_called_once()
		self.mock_db_get_value.assert_called_with('Twilio Agents', 'test_user@example.com', 'twilio_number')
		mock_twilio.generate_voice_access_token.assert_called_once_with(identity='test_user@example.com')

		self.assertEqual(result, {'token': 'mock_token'})

	def test_generate_access_token_no_connection(self):
		# Setup
		self.mock_connect.return_value = None

		# Execute
		result = generate_access_token()

		# Verify
		self.assertEqual(result, {})

	def test_generate_access_token_no_number(self):
		# Setup
		mock_twilio = MagicMock()
		self.mock_connect.return_value = mock_twilio

		# Setup to return no twilio number
		self.mock_db_get_value.return_value = None

		# Execute
		result = generate_access_token()

		# Verify
		self.assertEqual(result, {
			"ok": False,
			"error": "caller_phone_identity_missing",
			"detail": "Phone number is not mapped to the caller"
		})

if __name__ == '__main__':
	unittest.main()
