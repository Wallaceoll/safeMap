# SafeMap

O SafeMap é uma plataforma de segurança urbana e mapeamento social dedicada a oferecer informações em tempo real sobre locais de apoio e zonas de proteção. A plataforma foi projetada para proporcionar uma experiência de navegação amigável, conectando cidadãos a redes de suporte com eficiência, elegância e segurança em cada detalhe.

## Descrição

O SafeMap foca na excelência da jornada do usuário, desde a identificação de rotas seguras até o relato de ocorrências comunitárias. O ecossistema oferece funcionalidades pensadas para a proteção social:
- Mapeamento dinâmico de locais de apoio (Mulheres, LGBT+, etc).
- Sistema de relatos de incidentes em tempo real.
- Interface intuitiva focada em acessibilidade e resposta rápida.

## Instalação

Para configurar o ambiente do SafeMap localmente, temos alguns critérios e passos a seguir:

1. **Instale uma IDE**: Recomendamos o uso do IntelliJ IDEA (preferencial), Eclipse ou VS Code.
2. **Configure o Maven**: O uso do Maven 3.9+ instalado globalmente é recomendado. ([Download Maven](https://maven.apache.org/download.cgi)) *(Nota: o projeto também possui o Maven Wrapper `./mvnw` embutido para facilitar).*
3. **Java JDK 21**: O projeto utiliza o JDK 21 (Recomendado: Amazon Corretto 21). ([Download Corretto 21](https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html))

## Configuração do Projeto para Desenvolvimento Local

1. Verifique a variável de sistema `JAVA_HOME` e certifique-se de que aponta para o JDK 21.
2. Verifique a variável de sistema `PATH` e certifique-se de que inclua o caminho `/bin` do seu JDK e do Maven.
3. Crie ou verifique a variável `MAVEN_HOME` apontando para sua instalação local do Maven.

O comando a seguir compila o projeto e prepara o ambiente. Recomenda-se executar este comando na pasta raiz do projeto antes do primeiro uso:

```bash
mvn clean install
```

### Windows / OSX - Executando a Aplicação

Como a arquitetura do projeto foi atualizada para o padrão unificado (o Frontend agora é servido diretamente pelo Backend no ecossistema Spring Boot), você só precisa executar um único comando para subir tudo:

1. Acesse o terminal na pasta raiz do projeto (`/safeMap`).
2. Execute o comando para iniciar o serviço na porta 8080: 
   ```bash
   mvn spring-boot:run
   ```
3. Acesse a aplicação no seu navegador: `http://localhost:8080` (A interface de usuário já será carregada automaticamente).

Após completar estes passos, seu ambiente estará pronto para testes e desenvolvimento.
