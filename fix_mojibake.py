#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import glob

def fix_mojibake_file(file_path):
    """
    Fix double UTF-8 encoding (Mojibake)
    The issue: UTF-8 bytes were misinterpreted as Latin-1, then re-encoded to UTF-8
    Solution: Read as UTF-8, get bytes, interpret as Latin-1, re-encode as UTF-8
    """
    try:
        # Read file as UTF-8 bytes
        with open(file_path, 'rb') as f:
            utf8_bytes = f.read()
        
        # Try to decode as UTF-8 first
        utf8_string = utf8_bytes.decode('utf-8')
        
        # Check if it contains mojibake patterns
        if 'ðŸ' in utf8_string or 'Â©' in utf8_string or 'â' in utf8_string:
            # This is mojibake - apply fix
            # Get the UTF-8 bytes as if they were Latin-1 string
            latin1_string = utf8_bytes.decode('latin-1')
            
            # Now encode back to UTF-8
            fixed_bytes = latin1_string.encode('utf-8')
            
            # Write back
            with open(file_path, 'wb') as f:
                f.write(fixed_bytes)
            
            return True, "Fixed"
        else:
            return False, "No mojibake found"
    
    except Exception as e:
        return False, f"Error: {str(e)}"

# Main
base_dir = r"C:\Users\sahti\OneDrive\Desktop\sem3\opps\java project\src\main\resources\templates"
html_files = glob.glob(os.path.join(base_dir, "*.html"))

print(f"Processing {len(html_files)} HTML files...")
fixed_count = 0

for file_path in sorted(html_files):
    result, message = fix_mojibake_file(file_path)
    file_name = os.path.basename(file_path)
    if result:
        print(f"  ✓ {file_name}: {message}")
        fixed_count += 1
    else:
        print(f"  - {file_name}: {message}")

print(f"\nTotal fixed: {fixed_count}/{len(html_files)}")
