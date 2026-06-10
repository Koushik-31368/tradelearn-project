# ---- Stage 1: Build the React App ----
# Use an official Node.js image as the builder
FROM node:18-alpine as builder

# Set the working directory in the container
WORKDIR /app

# Copy the package.json and package-lock.json files
COPY package*.json ./

# Install the project dependencies
RUN npm install

# Copy the rest of the application source code
COPY . .

# Build the application for production
RUN npm run build

# ---- Stage 2: Serve the App with Nginx ----
# Use a lightweight Nginx image for the final production image
FROM nginx:stable-alpine

# Copy the built files from the 'builder' stage into Nginx's web server directory
COPY --from=builder /app/build /usr/share/nginx/html

# Tell Docker that the container will listen on port 80
EXPOSE 80

# The command to start the Nginx server when the container starts
CMD ["nginx", "-g", "daemon off;"]