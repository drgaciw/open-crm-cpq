import time
import frappe

def run_benchmark():
    # Setup test data
    frappe.init(site='test_site')
    frappe.connect()

    frappe.db.begin()

    # Need to verify if whatsapp is enabled or maybe mock some stuff?
    pass

if __name__ == '__main__':
    run_benchmark()
