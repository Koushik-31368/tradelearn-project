const fs = require('fs');
const path = require('path');

const srcDir = path.join(__dirname, 'src');

function getAllFiles(dirPath, arrayOfFiles) {
  const files = fs.readdirSync(dirPath);

  arrayOfFiles = arrayOfFiles || [];

  files.forEach(function(file) {
    if (fs.statSync(dirPath + "/" + file).isDirectory()) {
      arrayOfFiles = getAllFiles(dirPath + "/" + file, arrayOfFiles);
    } else {
      if (file.endsWith('.js') || file.endsWith('.jsx')) {
        arrayOfFiles.push(path.join(dirPath, "/", file));
      }
    }
  });

  return arrayOfFiles;
}

const files = getAllFiles(srcDir);

// Mapping of filename to new relative path from src/
const fileMap = {
  // Context
  'AuthContext': 'features/auth/AuthContext',

  // API
  'api': 'api/api',
  'authService': 'api/authService',
  'leaderboardService': 'api/leaderboardService',
  'marketService': 'api/marketService',
  'matchmakingService': 'api/matchmakingService',

  // Layout
  'Navbar': 'layout/components/Navbar',
  'Footer': 'layout/components/Footer',
  'Hero': 'layout/components/Hero',
  'Modal': 'layout/components/Modal',
  'StockTicker': 'layout/components/StockTicker',

  // Auth
  'LoginPage': 'features/auth/pages/LoginPage',
  'RegisterPage': 'features/auth/pages/RegisterPage',
  'ForgotPasswordPage': 'features/auth/pages/ForgotPasswordPage',

  // Matchmaking
  'LobbyPage': 'features/matchmaking/pages/LobbyPage',
  'CreateGameForm': 'features/matchmaking/components/CreateGameForm',

  // Game
  'GamePage': 'features/game/pages/GamePage',
  'MatchResultPage': 'features/game/pages/MatchResultPage',
  'LiveScoreboard': 'features/game/components/LiveScoreboard',
  'StockChart': 'features/game/components/StockChart',

  // Dashboard
  'HomePage': 'features/dashboard/pages/HomePage',
  'ProfilePage': 'features/dashboard/pages/ProfilePage',
  'MatchHistoryPage': 'features/dashboard/pages/MatchHistoryPage',
  'DashboardPanel': 'features/dashboard/components/DashboardPanel',
  'DailyCheckinModal': 'features/dashboard/components/DailyCheckinModal',

  // Leaderboard
  'LeaderboardPage': 'features/leaderboard/pages/LeaderboardPage',
  'TierBadge': 'features/leaderboard/components/TierBadge',
  'TopTraders': 'features/leaderboard/components/TopTraders',

  // Learn
  'LearnPage': 'features/learn/pages/LearnPage',

  // Simulator
  'SimulatorPage': 'features/simulator/pages/SimulatorPage',
  'MissionSelectionPage': 'features/simulator/pages/MissionSelectionPage',
  'MissionDashboard': 'features/simulator/components/MissionDashboard',

  // Practice & Strategies
  'PracticePage': 'features/practice/pages/PracticePage',
  'StrategiesPage': 'features/strategies/pages/StrategiesPage',

  // Legal
  'TermsPage': 'features/legal/pages/TermsPage',
  'PrivacyPage': 'features/legal/pages/PrivacyPage',
  'RiskDisclosurePage': 'features/legal/pages/RiskDisclosurePage',
};

files.forEach(file => {
  let content = fs.readFileSync(file, 'utf8');
  let originalContent = content;

  // Regex to match imports: import ... from '...'
  const importRegex = /import\s+.*?\s+from\s+['"]([^'"]+)['"]/g;
  let match;
  
  while ((match = importRegex.exec(content)) !== null) {
    const importPath = match[1];
    
    // Ignore absolute imports from node_modules
    if (!importPath.startsWith('.')) continue;

    const baseName = path.basename(importPath, path.extname(importPath));
    
    if (fileMap[baseName]) {
      // Calculate relative path from current file to the new location
      const targetAbsPath = path.join(srcDir, fileMap[baseName]);
      const currentDir = path.dirname(file);
      let newRelativePath = path.relative(currentDir, targetAbsPath).replace(/\\/g, '/');
      
      if (!newRelativePath.startsWith('.')) {
        newRelativePath = './' + newRelativePath;
      }
      
      // Replace the exact import path string
      const exactMatchRegex = new RegExp(`(['"])${importPath}(['"])`, 'g');
      content = content.replace(exactMatchRegex, `$1${newRelativePath}$2`);
    }
  }

  // Handle CSS imports
  const cssImportRegex = /import\s+['"]([^'"]+\.css)['"]/g;
  while ((match = cssImportRegex.exec(content)) !== null) {
    const importPath = match[1];
    
    // For CSS, we usually import from the same directory now since we moved .css with .jsx
    // Wait, the .css files are in the same dir as the .jsx files. 
    // If the old import was './Component.css' or '../components/Component.css',
    // We just replace it with './Component.css' because they are collocated.
    const baseName = path.basename(importPath);
    const newRelativePath = './' + baseName;
    
    const exactMatchRegex = new RegExp(`(['"])${importPath}(['"])`, 'g');
    content = content.replace(exactMatchRegex, `$1${newRelativePath}$2`);
  }

  if (content !== originalContent) {
    fs.writeFileSync(file, content, 'utf8');
    console.log(`Updated imports in ${path.relative(srcDir, file)}`);
  }
});
