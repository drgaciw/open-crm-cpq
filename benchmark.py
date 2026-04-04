import time
import sys
from unittest.mock import MagicMock
import types

# Create a mock frappe package structure
frappe_mock = types.ModuleType("frappe")
sys.modules['frappe'] = frappe_mock

translate_mock = types.ModuleType("frappe.translate")
translate_mock.get_all_translations = MagicMock()
sys.modules['frappe.translate'] = translate_mock

utils_mock = types.ModuleType("frappe.utils")
utils_mock.get_url = MagicMock()
utils_mock.cstr = MagicMock()
utils_mock.make_filter_tuple = MagicMock()
utils_mock.format_duration = MagicMock()
utils_mock.get_formatted_email = MagicMock()
utils_mock.get_files_path = MagicMock()
utils_mock.get_site_path = MagicMock()
sys.modules['frappe.utils'] = utils_mock

model_mock = types.ModuleType("frappe.model")
model_mock.no_value_fields = []
model_mock.table_fields = []
sys.modules['frappe.model'] = model_mock

doc_mock = types.ModuleType("frappe.model.document")
doc_mock.get_controller = MagicMock()
class MockDocument:
    def __init__(self, *args, **kwargs):
        pass
doc_mock.Document = MockDocument
sys.modules['frappe.model.document'] = doc_mock

core_mock = types.ModuleType("frappe.core")
sys.modules['frappe.core'] = core_mock

doctype_mock = types.ModuleType("frappe.core.doctype")
sys.modules['frappe.core.doctype'] = doctype_mock

comm_mock = types.ModuleType("frappe.core.doctype.communication")
sys.modules['frappe.core.doctype.communication'] = comm_mock

email_mock = types.ModuleType("frappe.core.doctype.communication.email")
sys.modules['frappe.core.doctype.communication.email'] = email_mock

# We need _dict from frappe
class _dict(dict):
    def __getattr__(self, key):
        return self.get(key)
    def __setattr__(self, key, value):
        self[key] = value

frappe_mock._dict = _dict
frappe_mock.ValidationError = Exception
frappe_mock.PermissionError = Exception
frappe_mock._ = lambda x: x

db_mock = MagicMock()
db_mock.exists.return_value = True
frappe_mock.db = db_mock

frappe_mock.has_permission = MagicMock(return_value=True)
frappe_mock.get_all = MagicMock()
frappe_mock.get_doc = MagicMock()
frappe_mock.throw = MagicMock(side_effect=Exception)
frappe_mock.new_doc = MagicMock()
frappe_mock.qb = MagicMock()
frappe_mock.render_template = MagicMock()
frappe_mock.client = MagicMock()

def whitelist(*args, **kwargs):
    def decorator(fn):
        return fn
    return decorator

frappe_mock.whitelist = whitelist

# Add cpq_beta to path so we can import properly
sys.path.insert(0, '/app/cpq_beta')

from crm.api.whatsapp import get_whatsapp_messages

# Setup mock data
def setup_mocks(num_messages=50, num_templates=5):
    messages = []
    for i in range(num_messages):
        messages.append({
            "name": f"MSG-{i}",
            "type": "Outgoing",
            "to": "+1234567890",
            "from": "+0987654321",
            "content_type": "text",
            "message_type": "Template",
            "attach": "",
            "template": f"TPL-{i % num_templates}",
            "use_template": 1,
            "message_id": f"id-{i}",
            "is_reply": 0,
            "reply_to_message_id": None,
            "creation": "2023-01-01 10:00:00",
            "message": "",
            "status": "Sent",
            "reference_doctype": "CRM Deal",
            "reference_name": "DEAL-1",
            "template_parameters": '["param1", "param2"]',
            "template_header_parameters": '["header1"]',
        })

    frappe_mock.get_all.return_value = messages

    def mock_get_doc(doctype, name=None, *args, **kwargs):
        if doctype == "WhatsApp Templates":
            # Simulate a slow db call
            time.sleep(0.001)
            return _dict({
                "name": name,
                "template_name": f"Template {name}",
                "template": "Hello {{1}}, welcome to {{2}}",
                "header": "Hi {{1}}",
                "footer": "Bye",
            })
        elif doctype == "CRM Deal":
            return _dict({
                "name": name,
                "lead_name": "John Doe",
                "contacts": []
            })
        return _dict()

    frappe_mock.get_doc.side_effect = mock_get_doc

def run_benchmark():
    setup_mocks(num_messages=500, num_templates=10)

    # Reset call counts
    frappe_mock.get_doc.reset_mock()

    start_time = time.perf_counter()
    messages = get_whatsapp_messages("CRM Deal", "DEAL-1")
    end_time = time.perf_counter()

    # Count get_doc calls for WhatsApp Templates specifically
    template_calls = [call for call in frappe_mock.get_doc.call_args_list if call[0][0] == "WhatsApp Templates"]
    print(f"Time taken: {(end_time - start_time) * 1000:.2f} ms")
    print(f"WhatsApp Templates get_doc calls: {len(template_calls)}")
    print(f"Total processed template messages: {len([m for m in messages if m.get('message_type') == 'Template'])}")

if __name__ == '__main__':
    run_benchmark()
