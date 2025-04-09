# Define variables
REPO="star4droid/Star2D"
BRANCH="master"
TARGET_PATH="app/src/main/java"
LOCAL_PATH="./"

# Iterate over files in the local folder
for FILE in $(find $LOCAL_PATH -type f); do
  # Get the relative file path
  RELATIVE_PATH=${FILE#$LOCAL_PATH/}

  # Read file content and encode it in base64
  LOCAL_CONTENT=$(base64 -w 0 "$FILE")

  # Check if the file exists in the repository
  API_RESPONSE=$(gh api "/repos/$REPO/contents/$TARGET_PATH/$RELATIVE_PATH" --jq '.' 2>/dev/null || echo "null")

  if [ "$API_RESPONSE" = "null" ]; then
    # File is not available in the repository
    echo "$RELATIVE_PATH: not available"
  else
    # Extract the remote content from the API response
    REMOTE_CONTENT=$(echo "$API_RESPONSE" | jq -r '.content' | tr -d '\n')
    
    if [ "$LOCAL_CONTENT" = "$REMOTE_CONTENT" ]; then
      # File exists and content matches
      echo "$RELATIVE_PATH: available"
    else
      # File exists but content differs
      echo "$RELATIVE_PATH: needs update"
    fi
  fi
done