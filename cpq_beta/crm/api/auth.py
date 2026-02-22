import frappe
from urllib.parse import urlparse


def _is_safe_icon_url(url):
	parsed = urlparse(url or "")
	return parsed.scheme in {"http", "https"} and bool(parsed.netloc)

@frappe.whitelist(allow_guest=True)
def oauth_providers():
	from frappe.utils.password import get_decrypted_password
	from frappe.utils.oauth import get_oauth2_authorize_url, get_oauth_keys

	out = []
	providers = frappe.get_all(
		"Social Login Key",
		filters={"enable_social_login": 1},
		fields=["name", "client_id", "base_url", "provider_name", "icon"],
		order_by="name",
	)

	for provider in providers:
		client_secret = get_decrypted_password("Social Login Key", provider.name, "client_secret")
		if not client_secret:
			continue

		icon_url = provider.icon if _is_safe_icon_url(provider.icon) else None

		if provider.client_id and provider.base_url and get_oauth_keys(provider.name):
			out.append(
				{
					"name": provider.name,
					"provider_name": provider.provider_name,
					"auth_url": get_oauth2_authorize_url(provider.name, "/crm"),
					"icon_url": icon_url,
				}
			)
	return out
