use enigo::{Direction, Enigo, Key, Keyboard, Settings};

use futures::{SinkExt, StreamExt};
use tokio::{
    net::{TcpListener, TcpStream},
    signal,
    sync::mpsc,
};

use tokio_tungstenite::{accept_async, tungstenite::Message};

fn get_key(key_str: &str) -> enigo::Key {
    match key_str {
        "top" => Key::V,
        "bottom" => Key::Q,
        "left" => Key::V,
        "right" => Key::E,
        _ => Key::E,
    }
}

fn handle_commands(command: &str) -> Option<(enigo::Key, Direction)> {
    let splitted = command.split_once("_");

    match splitted {
        None => None,
        Some(splitted) => {
            if splitted.0 != "joystick" {
                let key = get_key(splitted.1);
                match splitted.0 {
                    "press" => Some((key, Direction::Press)),
                    "release" => Some((key, Direction::Release)),
                    _ => None,
                }
            } else {
                match splitted.1 {
                    "up" => Some((Key::UpArrow, Direction::Click)),
                    "down" => Some((Key::DownArrow, Direction::Click)),
                    "left" => Some((Key::LeftArrow, Direction::Click)),
                    "right" => Some((Key::RightArrow, Direction::Click)),
                    _ => None,
                }
            }
        }
    }
}

async fn handle_connection(tls_stream: TcpStream) {
    let ws_stream = accept_async(tls_stream).await.expect("Failed to accept");
    println!(
        "New WebSocket connection: {}",
        ws_stream.get_ref().peer_addr().unwrap()
    );

    let mut enigo = Enigo::new(&Settings::default()).unwrap();

    let (mut write, mut read) = ws_stream.split();

    while let Some(msg) = read.next().await {
        match msg {
            Ok(Message::Text(text)) => {
                let text_str = text.as_str().trim();
                // println!("Received: {}", text_str);
                if text_str == "ping" {
                    let response = Message::Text("pong".into());
                    write.send(response).await.expect("Failed to send message");
                    println!("Sent: pong");
                } else {
                    match handle_commands(text_str) {
                        Some((key, direction)) => {
                            let msg = format!("Failed to {:?} {:?}", direction, key);
                            enigo.key(key, direction).expect(msg.as_str());
                        }
                        None => {
                            if !text_str.starts_with("joystick") {
                                println!("Unknown message: {}", text_str);
                            }
                        }
                    }
                }
            }
            Ok(Message::Close(_)) => {
                println!("Client disconnected");
                break;
            }
            _ => {}
        }
    }
}

#[tokio::main]
async fn main() {
    let server_info = "0.0.0.0:12345";
    let listener = TcpListener::bind(server_info)
        .await
        .expect("Could not bind");

    println!("WebSocket Secure (WSS) server listening on {}", server_info);

    let (tx, mut rx) = mpsc::channel(1);

    tokio::spawn(async move {
        signal::ctrl_c().await.expect("Failed to listen for event");
        tx.send(()).await.expect("Failed to send shutdown signal");
    });

    loop {
        tokio::select! {
            _ = rx.recv() => {
                println!("Shutting down server...");
                break;
            }
            result = listener.accept() => {
                match result {
                    Ok((stream, _)) => {
                        tokio::spawn(async move {
                            handle_connection(stream).await;
                        });
                    }
                    Err(e) => {
                        println!("Failed to accept connection: {}", e);
                    }
                }
            }
        }
    }
}
