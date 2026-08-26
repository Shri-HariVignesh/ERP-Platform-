import QRCode from 'qrcode';

/** Renders a real, scannable QR as inline SVG. No external service, no image files. */
export const QrService = {
  svg(text, px) {
    try {
      const cells = QRCode.create(text, { errorCorrectionLevel: 'M' }).modules;
      const size = cells.size;
      let path = '';
      for (let y = 0; y < size; y++) {
        for (let x = 0; x < size; x++) {
          if (cells.get(x, y)) path += `M${x} ${y}h1v1h-1z`;
        }
      }
      return `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ${size} ${size}' width='${px}' `
        + `height='${px}' shape-rendering='crispEdges' role='img' aria-label='Verification QR code'>`
        + `<rect width='${size}' height='${size}' fill='#fff'/><path fill='#000' d='${path}'/></svg>`;
    } catch {
      return "<span class='muted'>QR unavailable</span>";
    }
  },
};
