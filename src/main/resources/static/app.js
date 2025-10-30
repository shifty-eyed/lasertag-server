const { createApp } = Vue;

createApp({
    data() {
        return {
            // Connection state
            connected: false,
            eventSource: null,

            // Game state
            gameState: {
                playing: false,
                timeLeftSeconds: 0,
                teamPlay: false,
                fragLimit: 10,
                timeLimitMinutes: 15,
                teamScores: {}
            },

            // Settings
            settings: {
                timeLimit: 15,
                fragLimit: 10,
                teamPlay: false
            },

            // Players data
            players: [],

            // Dispensers data
            dispensers: {
                health: [],
                ammo: []
            },

            // Dispenser settings
            dispenserSettings: {
                health: {
                    timeout: null,
                    amount: null
                },
                ammo: {
                    timeout: null,
                    amount: null
                }
            },

            // Team configuration
            teamNames: {
                0: 'Red',
                1: 'Blue',
                2: 'Green',
                3: 'Yellow',
                4: 'Magenta',
                5: 'Cyan'
            },
            teamColors: ['#DC143C', '#1E90FF', '#32CD32', '#FFD700', '#FF00FF', '#00CED1'],
            teamTextColors: ['#FFFFFF', '#FFFFFF', '#000000', '#000000', '#FFFFFF', '#000000']
        };
    },

    computed: {
        onlineHealthDispensers() {
            return this.dispensers.health
                .filter(d => d.online)
                .map(d => d.id);
        },
        onlineAmmoDispensers() {
            return this.dispensers.ammo
                .filter(d => d.online)
                .map(d => d.id);
        },
        sortedTeamScores() {
            if (!this.gameState.teamScores) return {};
            
            // Sort by score descending
            return Object.entries(this.gameState.teamScores)
                .sort((a, b) => b[1] - a[1])
                .reduce((acc, [key, val]) => {
                    acc[key] = val;
                    return acc;
                }, {});
        }
    },

    methods: {
        // Initialize SSE connection
        connectSSE() {
            if (this.eventSource) {
                this.eventSource.close();
            }

            this.eventSource = new EventSource('/api/events');

            this.eventSource.addEventListener('game-state', (event) => {
                const data = JSON.parse(event.data);
                this.gameState = data;
                //this.settings.timeLimit = data.timeLimitMinutes;
                //this.settings.fragLimit = data.fragLimit;
                //this.settings.teamPlay = data.teamPlay;
            });

            this.eventSource.addEventListener('players', (event) => {
                const data = JSON.parse(event.data);
                this.players = data;
            });

            this.eventSource.addEventListener('dispensers', (event) => {
                const data = JSON.parse(event.data);
                this.dispensers = data;
            });

            this.eventSource.onopen = () => {
                this.connected = true;
                console.log('SSE connection established');
            };

            this.eventSource.onerror = (error) => {
                console.error('SSE error:', error);
                this.connected = false;
                
                // Attempt to reconnect after 3 seconds
                setTimeout(() => {
                    if (!this.connected) {
                        console.log('Attempting to reconnect...');
                        this.connectSSE();
                    }
                }, 3000);
            };
        },

        // API calls
        async startGame() {
            try {
                const response = await fetch('/api/game/start', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        timeLimit: this.settings.timeLimit,
                        fragLimit: this.settings.fragLimit,
                        teamPlay: this.settings.teamPlay
                    })
                });
                
                if (!response.ok) {
                    throw new Error('Failed to start game');
                }
                
                console.log('Game started');
            } catch (error) {
                console.error('Error starting game:', error);
                alert('Failed to start game');
            }
        },

        async endGame() {
            try {
                const response = await fetch('/api/game/end', {
                    method: 'POST'
                });
                
                if (!response.ok) {
                    throw new Error('Failed to end game');
                }
                
                console.log('Game ended');
            } catch (error) {
                console.error('Error ending game:', error);
                alert('Failed to end game');
            }
        },

        async updatePlayer(player) {
            try {
                const response = await fetch(`/api/players/${player.id}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        name: player.name,
                        teamId: player.teamId,
                        damage: player.damage,
                        bulletsMax: player.bulletsMax
                    })
                });
                
                if (!response.ok) {
                    throw new Error('Failed to update player');
                }
                
                console.log('Player updated:', player.name);
            } catch (error) {
                console.error('Error updating player:', error);
                alert('Failed to update player');
            }
        },

        async updateDispensers(type) {
            const settings = this.dispenserSettings[type];
            
            // Only send if at least one value is set
            if (!settings.timeout && !settings.amount) {
                return;
            }

            try {
                const response = await fetch(`/api/dispensers/${type}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        timeout: settings.timeout,
                        amount: settings.amount
                    })
                });
                
                if (!response.ok) {
                    throw new Error('Failed to update dispensers');
                }
                
                console.log(`${type} dispensers updated`);
                
                // Clear the fields after successful update
                settings.timeout = null;
                settings.amount = null;
            } catch (error) {
                console.error('Error updating dispensers:', error);
                alert('Failed to update dispensers');
            }
        },

        // Utility methods
        formatTime(seconds) {
            const minutes = Math.floor(seconds / 60);
            const secs = seconds % 60;
            return `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
        },

        getTeamName(teamId) {
            return this.teamNames[teamId] || 'Unknown';
        },

        getTeamColor(teamId) {
            return this.teamColors[teamId] || '#888888';
        },

        getTeamTextColor(teamId) {
            return this.teamTextColors[teamId] || '#000000';
        },

        // Initial data load
        async loadInitialData() {
            try {
                // Load game state
                const stateResponse = await fetch('/api/game/state');
                if (stateResponse.ok) {
                    this.gameState = await stateResponse.json();
                    this.settings.timeLimit = this.gameState.timeLimitMinutes;
                    this.settings.fragLimit = this.gameState.fragLimit;
                    this.settings.teamPlay = this.gameState.teamPlay;
                }

                // Load players
                const playersResponse = await fetch('/api/players');
                if (playersResponse.ok) {
                    this.players = await playersResponse.json();
                }

                // Load dispensers
                const dispensersResponse = await fetch('/api/dispensers');
                if (dispensersResponse.ok) {
                    this.dispensers = await dispensersResponse.json();
                }
            } catch (error) {
                console.error('Error loading initial data:', error);
            }
        }
    },

    mounted() {
        // Load initial data
        this.loadInitialData();
        
        // Connect to SSE
        this.connectSSE();
    },

    beforeUnmount() {
        // Clean up SSE connection
        if (this.eventSource) {
            this.eventSource.close();
        }
    }
}).mount('#app');

