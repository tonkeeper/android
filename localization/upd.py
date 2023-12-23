import json
import os
import re

# Directory containing the JSON files
json_files_dir = 'files'

# Base directory for the localization folders, relative to the script's location
localization_dir = 'src/main/res'

def convert_placeholders(value):
    # Find all placeholders like %{something}
    placeholders = re.findall(r'%\{[^\}]+\}', value)

    # Replace each placeholder with %1$s, %2$s, etc.
    for i, placeholder in enumerate(placeholders, start=1):
        value = value.replace(placeholder, f'%{i}$s')

    return value

def convert_to_android_strings(json_data):
    android_strings = ['<resources>']
    for key, value in json_data.items():
        # Skip empty keys or None values
        if key and value is not None:
            # Replace hyphens with underscores in key names
            key = key.replace('-', '_')

            # Escape special XML characters
            value = (value.replace("&", "&amp;")
                     .replace("<", "&lt;")
                     .replace(">", "&gt;")
                     .replace("\n", "\\n")
                     .replace(" ", " "))  # Handle non-breaking spaces

            # Replace custom placeholders with Android placeholders
            value = convert_placeholders(value)

            # Escaping single quotes if needed
            value = value.replace("'", "\\'")

            android_string = f'    <string name="{key}">{value}</string>'
            android_strings.append(android_string)
    android_strings.append('</resources>')
    return '\n'.join(android_strings)




def create_localization_folders():
    for filename in os.listdir(json_files_dir):
        if filename.endswith('.json'):
            # Extract the first two letters of the language code from the filename
            lang_code = filename.split('.')[0][:2]
            folder_name = 'values' if lang_code == 'en' else f'values-{lang_code}'
            folder_path = os.path.join(localization_dir, folder_name)

            # Create the folder if it doesn't exist
            os.makedirs(folder_path, exist_ok=True)

            # Read JSON file and convert it
            with open(os.path.join(json_files_dir, filename), 'r') as file:
                json_data = json.load(file)
                android_strings = convert_to_android_strings(json_data)

            # Write to strings.xml file
            with open(os.path.join(folder_path, 'strings.xml'), 'w') as file:
                file.write(android_strings)

# Make sure to call the function from within the script
if __name__ == "__main__":
    create_localization_folders()
