const { createApp } = Vue;

createApp({
    data() {
        return {
            connected: false,
            eventSource: null,

            gameState: {
                playing: false,
                timeLeftSeconds: 0,
                teamScores: {}
            },

            players: [],

            dispensers: {
                health: [],
                ammo: []
            },

            settings: {
                general: {
                    fragLimit: 10,
                    teamPlay: false,
                    timeLimitMinutes: 15
                },
                players: {},
                dispensers: {
                    health: {
                        timeout: 60,
                        amount: 40
                    },
                    ammo: {
                        timeout: 60,
                        amount: 40
                    }
                }
            },

            teamNames: {
                0: 'Red',
                1: 'Blue',
                2: 'Green',
                3: 'Yellow',
                4: 'Magenta',
                5: 'Cyan'
            },
            teamColors: ['#DC143C', '#1E90FF', '#32CD32', '#FFD700', '#FF00FF', '#00CED1'],
            teamTextColors: ['#FFFFFF', '#FFFFFF', '#000000', '#000000', '#FFFFFF', '#000000'],

            logs: []
        };
    },

    computed: {
        onlineHealthDispensers() {
            return this.dispensers.health || [];
        },
        onlineAmmoDispensers() {
            return this.dispensers.ammo || [];
        },
        sortedTeamScores() {
            return this.gameState.teamScores || {};
        },
        gameStatus() {
            if (!this.connected) {
                return 'disconnected';
            }
            return this.gameState.playing ? 'game' : 'idle';
        },
        dispenserSettings() {
            return this.settings.dispensers || {
                health: { timeout: 60, amount: 40 },
                ammo: { timeout: 60, amount: 40 }
            };
        },
        gameStatusText() {
            switch (this.gameStatus) {
                case 'disconnected':
                    return '🔴 Disconnected';
                case 'game':
                    return '🎮 Game Active';
                case 'idle':
                    return '🟢 Idle';
                default:
                    return 'Unknown';
            }
        }
    },

    methods: {
        connectSSE() {
            if (this.eventSource) {
                this.eventSource.close();
            }

            this.eventSource = new EventSource('/api/events');

            this.eventSource.addEventListener('isPlaying', (event) => {
                this.gameState.playing = JSON.parse(event.data);
                console.log('Got gameState.playing:', this.gameState.playing);
            });

            this.eventSource.addEventListener('players', (event) => {
				this.players = JSON.parse(event.data);
				console.log('Got players:', this.players);

				const teamTotals = this.players.reduce((acc, player) => {
					const teamId = player.teamId;
					if (teamId === null || teamId === undefined || teamId < 0) {
						return acc;
					}

					if (!Object.prototype.hasOwnProperty.call(acc, teamId)) {
						acc[teamId] = 0;
					}
					acc[teamId] += player.score || 0;
					return acc;
				}, {});

				const sortedTeamTotals = Object.fromEntries(
					Object.entries(teamTotals).sort((a, b) => b[1] - a[1])
				);

				this.gameState.teamScores = sortedTeamTotals;
				console.log('Computed teamScores:', this.gameState.teamScores);
            });

            this.eventSource.addEventListener('timeLeft', (event) => {
                this.gameState.timeLeftSeconds = Number(event.data);
                this.gameState.playing = this.gameState.timeLeftSeconds > 0;
                console.log('Got timeLeft:', this.gameState.timeLeftSeconds);
            });

            this.eventSource.addEventListener('dispensers', (event) => {
                this.dispensers = JSON.parse(event.data);
                console.log('Got dispensers:', this.dispensers);
            });

            this.eventSource.addEventListener('settings', (event) => {
                const settings = JSON.parse(event.data);
                this.settings = settings;
                console.log('Got settings:', this.settings);
            });

            this.eventSource.addEventListener('log', (event) => {
                const logMessage = JSON.parse(event.data);
                console.log('Got log:', logMessage);
                console.log('Total logs:', this.logs.length);
                this.logs.push(logMessage);
                // Auto-scroll to bottom
                this.$nextTick(() => {
                    if (this.$refs.logContent) {
                        this.$refs.logContent.scrollTop = this.$refs.logContent.scrollHeight;
                    }
                });
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
        async fetchSettings() {
            try {
                const response = await fetch('/api/settings');
                if (response.ok) {
                    const settings = await response.json();
                    this.settings = settings;
                }
            } catch (error) {
                console.error('Error fetching settings:', error);
            }
        },

        async startGame() {
            try {
                const response = await fetch('/api/game/start', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        timeLimit: this.settings.general.timeLimitMinutes,
                        fragLimit: this.settings.general.fragLimit,
                        teamPlay: this.settings.general.teamPlay
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
            const settings = this.settings.dispensers[type];

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
            } catch (error) {
                console.error('Error updating dispensers:', error);
                alert('Failed to update dispensers');
            }
        },

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

        clearLogs() {
            this.logs = [];
        },

        getLogLevelClass(log) {
            if (log.includes('ERROR')) {
                return 'log-error';
            } else if (log.includes('WARN')) {
                return 'log-warn';
            } else if (log.includes('INFO')) {
                return 'log-info';
            } else if (log.includes('DEBUG')) {
                return 'log-debug';
            }
            return '';
        }
    },

    mounted() {
        // Fetch initial settings
        this.fetchSettings();
        // Connect to SSE (initial data is sent automatically)
        this.connectSSE();
    },

    beforeUnmount() {
        // Clean up SSE connection
        if (this.eventSource) {
            this.eventSource.close();
        }
    }
}).mount('#app');

