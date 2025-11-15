# Web UI for Laser Tag Server

This document describes the new web-based administration console for the Laser Tag Server.

## Overview

The Laser Tag Server now supports two UIs that work simultaneously:
- **Swing UI**: Desktop application window with Java Swing
- **Web UI**: Browser-based responsive interface accessible from any device

Both UIs provide the same functionality for managing the game server, and you can use both at the same time!

## Using the UIs

### Starting the Server

1. Start the application:
   ```bash
   mvn spring-boot:run
   ```
   or
   ```bash
   ./start.sh
   ```

2. Both UIs will be available:
   - **Swing UI**: Opens automatically as a desktop window
   - **Web UI**: Access in your browser at `http://localhost:8080`

You can use either one or both simultaneously. Changes made in one UI will be reflected in the other in real-time!

### Features

The Web UI provides all the same features as the Swing UI:

#### Game Controls
- **Time Limit**: Set game duration in minutes (1-60)
- **Frag Limit**: Set score limit for winning (1-100)
- **Team Play**: Enable/disable team-based gameplay
- **Start/End Game**: Control game state

#### Team Scores
- Real-time display of team scores when team play is enabled
- Color-coded by team (Red, Blue, Green, Yellow, Magenta, Cyan)
- Automatically sorted by score

#### Players Management
- View all players with their current stats
- Edit player settings:
  - Name
  - Team assignment
  - Max bullets
  - Damage
- Real-time status indicators (Online/Offline)
- View scores, health, and assigned respawn points

#### Dispensers Management
- Configure Health Dispensers:
  - Timeout (seconds between dispenses)
  - Amount (health/ammo provided per dispense)
- Configure Ammo Dispensers:
  - Same settings as health dispensers
- View online/offline status of each dispenser

### Mobile Support

The Web UI is fully responsive and mobile-friendly:
- **Mobile devices**: Card-based layout for easy touch interaction
- **Tablets/Desktop**: Table view for efficient data management
- Minimum 44px touch targets for accessibility
- Optimized for portrait and landscape orientations

### Real-Time Updates

The Web UI uses Server-Sent Events (SSE) for real-time updates:
- Game state changes
- Player statistics
- Dispenser status
- Team scores
- Time remaining

Updates are pushed automatically from the server with no need to refresh the page.

### Browser Compatibility

The Web UI works with modern browsers:
- Chrome/Edge (v90+)
- Firefox (v88+)
- Safari (v14+)
- Mobile browsers (iOS Safari, Chrome Mobile)

### Architecture

The Web UI implementation consists of:

**Backend:**
- `SseEventService.java`: Manages Server-Sent Events connections
- `GameController.java`: REST API endpoints for game control
- `WebAdminConsole.java`: Bridge between game logic and web clients

**Frontend:**
- `index.html`: Main HTML structure
- `app.js`: Vue.js application with SSE connection and API calls
- `styles.css`: Mobile-first responsive styling

**Configuration:**
- Both UIs run simultaneously and independently
- Real-time synchronization between Swing and Web UIs
- No dependencies on external build tools (uses Vue.js CDN)

## Port Configuration

The web server runs on port 8080 by default. To change it, edit `application.properties`:

```properties
server.port=8080
```

## Troubleshooting

### Web UI not loading
- Check that port 8080 is not in use by another application
- Look for errors in the console output
- Try accessing http://localhost:8080 directly

### Connection lost
- Check your network connection
- Verify the server is still running
- The UI will attempt to reconnect automatically every 3 seconds

### Changes not updating
- Ensure the game is not running when editing player settings
- Check browser console for error messages
- Verify the SSE connection is active (green indicator at bottom)

## Development Notes

### Adding new features
When adding features that need to be reflected in both UIs:
1. Update the backend logic in `Game.java` and related classes
2. For Swing: Update `AdminConsole.java`
3. For Web: Update `WebAdminConsole.java`, add API endpoints in `GameController.java`, and update frontend files

### Security Note
The current implementation has no authentication or authorization. This is acceptable for local network use but should NOT be exposed to the public internet without adding proper security measures.

