# Push this repo to GitHub (rishabhsingh7320)

I can’t log in to your GitHub from here. After the first commit is created locally, do this on your machine:

## 1. Create a new repository on GitHub

1. Open https://github.com/new  
2. Owner: **rishabhsingh7320**  
3. Repository name: e.g. **`evoclaw`**, **`openclaw`**, or **`ai_code`**  
4. **Do not** add a README, .gitignore, or license (this repo already has them).  
5. Click **Create repository**.

## 2. Connect and push (HTTPS)

```bash
cd /Users/rishabhsingh/ai_code
git remote add origin https://github.com/rishabhsingh7320/YOUR_REPO_NAME.git
git branch -M main
git push -u origin main
```

GitHub will ask for authentication: use a **Personal Access Token** (not your password) or GitHub CLI (`gh auth login`).

## 2b. Or SSH

```bash
git remote add origin git@github.com:rishabhsingh7320/YOUR_REPO_NAME.git
git branch -M main
git push -u origin main
```

## Security checklist

- **`.env` is in `.gitignore`** — API keys should not be pushed.  
- If you ever committed secrets, rotate keys and use `git filter-repo` or BFG to purge history.  
- Your GitHub profile: https://github.com/rishabhsingh7320/
