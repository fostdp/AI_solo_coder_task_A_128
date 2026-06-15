const CanvasIcons = {
    createCaravanIcon(size = 32, direction = 0, state = 'normal') {
        const canvas = document.createElement('canvas');
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext('2d');

        ctx.save();
        ctx.translate(size / 2, size / 2);
        ctx.rotate(direction * Math.PI / 180);

        const bodyColor = state === 'alert' ? '#ef4444' :
                         state === 'resting' ? '#f59e0b' : '#e94560';

        ctx.fillStyle = bodyColor;
        ctx.beginPath();
        ctx.ellipse(0, 2, size * 0.35, size * 0.2, 0, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = bodyColor;
        ctx.beginPath();
        ctx.ellipse(-size * 0.1, -size * 0.1, size * 0.12, size * 0.15, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.ellipse(size * 0.1, -size * 0.1, size * 0.12, size * 0.15, 0, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#fbbf24';
        ctx.beginPath();
        ctx.ellipse(-size * 0.15, -size * 0.05, size * 0.08, size * 0.06, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.ellipse(size * 0.15, -size * 0.05, size * 0.08, size * 0.06, 0, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = bodyColor;
        ctx.beginPath();
        ctx.moveTo(size * 0.25, size * 0.05);
        ctx.lineTo(size * 0.38, -size * 0.08);
        ctx.lineTo(size * 0.38, size * 0.02);
        ctx.closePath();
        ctx.fill();

        ctx.fillStyle = '#16213e';
        ctx.beginPath();
        ctx.arc(size * 0.32, -size * 0.04, size * 0.02, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#16213e';
        ctx.fillRect(-size * 0.28, size * 0.1, size * 0.06, size * 0.12);
        ctx.fillRect(-size * 0.08, size * 0.1, size * 0.06, size * 0.12);
        ctx.fillRect(size * 0.04, size * 0.1, size * 0.06, size * 0.12);
        ctx.fillRect(size * 0.18, size * 0.1, size * 0.06, size * 0.12);

        if (state === 'alert') {
            ctx.fillStyle = 'rgba(239, 68, 68, 0.5)';
            ctx.beginPath();
            ctx.arc(0, 0, size * 0.55, 0, Math.PI * 2);
            ctx.fill();
        }

        ctx.restore();

        if (state === 'moving') {
            ctx.fillStyle = 'rgba(74, 222, 128, 0.8)';
            ctx.beginPath();
            ctx.arc(size * 0.85, size * 0.85, 4, 0, Math.PI * 2);
            ctx.fill();
        }

        return canvas;
    },

    createOasisIcon(size = 24) {
        const canvas = document.createElement('canvas');
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext('2d');

        const gradient = ctx.createRadialGradient(
            size / 2, size / 2, 0,
            size / 2, size / 2, size / 2
        );
        gradient.addColorStop(0, '#4ade80');
        gradient.addColorStop(0.6, '#22c55e');
        gradient.addColorStop(1, '#16a34a');

        ctx.fillStyle = gradient;
        ctx.beginPath();
        ctx.arc(size / 2, size / 2, size * 0.4, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#60a5fa';
        ctx.beginPath();
        ctx.ellipse(size / 2, size * 0.55, size * 0.2, size * 0.12, 0, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#166534';
        ctx.fillRect(size * 0.45, size * 0.2, size * 0.05, size * 0.25);
        ctx.beginPath();
        ctx.moveTo(size * 0.3, size * 0.2);
        ctx.lineTo(size * 0.48, size * 0.08);
        ctx.lineTo(size * 0.48, size * 0.25);
        ctx.closePath();
        ctx.fill();
        ctx.beginPath();
        ctx.moveTo(size * 0.65, size * 0.22);
        ctx.lineTo(size * 0.7, size * 0.32);
        ctx.lineTo(size * 0.52, size * 0.25);
        ctx.closePath();
        ctx.fill();

        ctx.strokeStyle = '#16a34a';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(size / 2, size / 2, size * 0.45, 0, Math.PI * 2);
        ctx.stroke();

        return canvas;
    },

    createWeatherStationIcon(size = 20, active = true) {
        const canvas = document.createElement('canvas');
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext('2d');

        const color = active ? '#60a5fa' : '#6b7280';

        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.arc(size / 2, size / 2, size * 0.35, 0, Math.PI * 2);
        ctx.fill();

        ctx.strokeStyle = 'rgba(96, 165, 250, 0.5)';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(size / 2, size / 2, size * 0.45, 0, Math.PI * 2);
        ctx.stroke();

        ctx.fillStyle = '#fff';
        ctx.font = `bold ${size * 0.4}px sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('W', size / 2, size / 2);

        return canvas;
    },

    createWaypointIcon(size = 18, isSupply = false) {
        const canvas = document.createElement('canvas');
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext('2d');

        const color = isSupply ? '#f59e0b' : '#a855f7';

        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.moveTo(size / 2, size * 0.1);
        ctx.lineTo(size * 0.85, size * 0.5);
        ctx.lineTo(size / 2, size * 0.9);
        ctx.lineTo(size * 0.15, size * 0.5);
        ctx.closePath();
        ctx.fill();

        ctx.fillStyle = '#fff';
        ctx.font = `bold ${size * 0.35}px sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(isSupply ? '驿' : '站', size / 2, size / 2);

        return canvas;
    },

    createAnimatedCaravan(size = 40, speed = 1) {
        const canvas = document.createElement('canvas');
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext('2d');

        let frame = 0;
        const totalFrames = 20;

        function draw() {
            ctx.clearRect(0, 0, size, size);

            const legOffset = Math.sin(frame * 0.5) * 2;
            const bobOffset = Math.sin(frame * 0.3) * 1;

            ctx.fillStyle = '#e94560';
            ctx.beginPath();
            ctx.ellipse(size / 2, size / 2 + bobOffset, size * 0.3, size * 0.18, 0, 0, Math.PI * 2);
            ctx.fill();

            ctx.fillStyle = '#e94560';
            ctx.beginPath();
            ctx.ellipse(size * 0.35, size * 0.35 + bobOffset, size * 0.1, size * 0.12, 0, 0, Math.PI * 2);
            ctx.fill();
            ctx.beginPath();
            ctx.ellipse(size * 0.6, size * 0.35 + bobOffset, size * 0.1, size * 0.12, 0, 0, Math.PI * 2);
            ctx.fill();

            ctx.fillStyle = '#fbbf24';
            ctx.beginPath();
            ctx.ellipse(size * 0.35, size * 0.42 + bobOffset, size * 0.07, size * 0.05, 0, 0, Math.PI * 2);
            ctx.fill();
            ctx.beginPath();
            ctx.ellipse(size * 0.6, size * 0.42 + bobOffset, size * 0.07, size * 0.05, 0, 0, Math.PI * 2);
            ctx.fill();

            ctx.fillStyle = '#16213e';
            const legY = size * 0.55 + bobOffset;
            ctx.fillRect(size * 0.22, legY + legOffset, size * 0.05, size * 0.1);
            ctx.fillRect(size * 0.32, legY - legOffset, size * 0.05, size * 0.1);
            ctx.fillRect(size * 0.58, legY + legOffset, size * 0.05, size * 0.1);
            ctx.fillRect(size * 0.68, legY - legOffset, size * 0.05, size * 0.1);

            ctx.fillStyle = '#e94560';
            ctx.beginPath();
            ctx.moveTo(size * 0.75, size * 0.45 + bobOffset);
            ctx.lineTo(size * 0.9, size * 0.3 + bobOffset);
            ctx.lineTo(size * 0.9, size * 0.42 + bobOffset);
            ctx.closePath();
            ctx.fill();

            frame = (frame + speed * 0.2) % totalFrames;
            requestAnimationFrame(draw);
        }

        draw();
        return canvas;
    }
};
