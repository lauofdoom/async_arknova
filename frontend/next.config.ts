import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // Forward API calls to the Spring Boot bot service
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.API_BASE_URL ?? 'http://localhost:8080'}/api/:path*`,
      },
    ];
  },
  images: {
    remotePatterns: [
      // Allow Discord CDN avatars
      { protocol: 'https', hostname: 'cdn.discordapp.com' },
      // Allow card images from community sources (update as needed)
      { protocol: 'https', hostname: '*.githubusercontent.com' },
    ],
  },
};

export default nextConfig;
